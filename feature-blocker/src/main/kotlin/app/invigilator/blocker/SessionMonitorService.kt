package app.invigilator.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.invigilator.core.intervention.InterventionEngine
import app.invigilator.core.intervention.InterventionLevel
import app.invigilator.core.session.ActiveSession
import app.invigilator.core.session.AppCategory
import app.invigilator.core.session.DistractionClassifier
import app.invigilator.core.session.DistractionEvent
import app.invigilator.core.session.DistractionRecord
import app.invigilator.core.session.SessionDoc
import app.invigilator.core.session.SessionEndReason
import app.invigilator.core.session.SessionStateRepository
import app.invigilator.core.session.SessionStats
import app.invigilator.core.session.SessionStatsRepository
import app.invigilator.core.session.SessionSummaryRepository
import app.invigilator.core.session.SessionType
import app.invigilator.feature.blocker.R
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SessionMonitorService : Service() {

    @Inject lateinit var sessionState: SessionStateRepository
    @Inject lateinit var classifier: DistractionClassifier
    @Inject lateinit var sessionStats: SessionStatsRepository
    @Inject lateinit var sessionSummaryRepo: SessionSummaryRepository
    @Inject lateinit var interventionEngine: InterventionEngine
    @Inject lateinit var batteryDiagnostic: BatteryDiagnostic

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var notificationManager: NotificationManager

    private var currentApp: String? = null
    private var currentAppCategory: AppCategory = AppCategory.UNKNOWN
    private var currentAppEnteredAt: Long = 0L
    private var lastInterventionLevel: InterventionLevel = InterventionLevel.NONE
    private var pollTickCount: Int = 0

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "session_monitor"
        private const val POLL_INTERVAL_MS = 2000L
        private const val DISTRACTION_DWELL_THRESHOLD_MS = 30_000L
        // Fire diagnostic every ~30s. With POLL_INTERVAL_MS=2000ms: 30000/2000 = 15 ticks.
        private const val DIAGNOSTIC_TICK_INTERVAL = 15
        const val ACTION_STOP = "app.invigilator.action.STOP_SESSION"
        const val ACTION_STOP_FROM_NAG = "app.invigilator.STOP_FROM_NAG"
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP_FROM_NAG) {
            Timber.d("SessionMonitorService: stop requested from nag notification")
            endSession(SessionEndReason.USER_ENDED)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        sessionStats.reset()
        currentApp = null
        lastInterventionLevel = InterventionLevel.NONE
        pollTickCount = 0
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                detectForegroundApp()
                tickInterventionEngine()
                checkTimerExpiry()
                pollTickCount++
                if (pollTickCount % DIAGNOSTIC_TICK_INTERVAL == 0) {
                    emitBatteryDiagnostic()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        Timber.d("SessionMonitor: started polling")
    }

    private fun tickInterventionEngine() {
        val app = currentApp ?: return
        val dwellSeconds = (System.currentTimeMillis() - currentAppEnteredAt) / 1000
        lastInterventionLevel = interventionEngine.onAppDwellTick(
            packageName = app,
            category = currentAppCategory,
            dwellSeconds = dwellSeconds,
        )
    }

    private fun detectForegroundApp() {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - POLL_INTERVAL_MS - 1000, now)
        var lastEventPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastEventPackage = event.packageName
            }
        }

        if (lastEventPackage != null && lastEventPackage != currentApp) {
            finalizePreviousApp(now)

            currentApp = lastEventPackage
            currentAppCategory = classifier.classify(lastEventPackage)
            currentAppEnteredAt = now

            Timber.d("SessionMonitor: foreground app -> $lastEventPackage ($currentAppCategory)")
        }
    }

    private fun checkTimerExpiry() {
        val active = sessionState.activeSession.value ?: return
        val type = active.sessionType
        if (type !is SessionType.Timed) return

        val elapsedMs = System.currentTimeMillis() - active.startedAtMillis
        val durationMs = type.durationMinutes * 60_000L

        if (elapsedMs >= durationMs) {
            Timber.d("SessionMonitor: timer expired (${type.durationMinutes}min)")
            endSession(SessionEndReason.TIMER_EXPIRED)
        }
    }

    private fun finalizePreviousApp(exitedAt: Long) {
        val previous = currentApp ?: return
        val dwellMs = exitedAt - currentAppEnteredAt
        val dwellSeconds = dwellMs / 1000

        val countsAsDistraction = (
            currentAppCategory == AppCategory.DISTRACTING ||
            currentAppCategory == AppCategory.UNKNOWN
        ) && dwellMs >= DISTRACTION_DWELL_THRESHOLD_MS

        if (countsAsDistraction) {
            sessionStats.recordDistractionEvent(
                DistractionEvent(
                    packageName = previous,
                    category = currentAppCategory,
                    enteredAtMillis = currentAppEnteredAt,
                    exitedAtMillis = exitedAt,
                    dwellSeconds = dwellSeconds,
                )
            )
            Timber.d("SessionMonitor: distraction recorded — $previous ($currentAppCategory) for ${dwellSeconds}s")
        } else if (currentAppCategory == AppCategory.DISTRACTING || currentAppCategory == AppCategory.UNKNOWN) {
            Timber.d("SessionMonitor: brief check (not counted) — $previous for ${dwellSeconds}s")
        }
    }

    private fun emitBatteryDiagnostic() {
        val active = sessionState.activeSession.value ?: return
        val elapsedSec = (System.currentTimeMillis() - active.startedAtMillis) / 1000L
        batteryDiagnostic.logSnapshot(
            sessionElapsedSeconds = elapsedSec,
            foregroundPackage = currentApp ?: "unknown",
            category = currentAppCategory.name,
            interventionLevel = lastInterventionLevel.name,
        )
    }

    private fun stopMonitoring() = endSession(SessionEndReason.USER_ENDED)

    private fun endSession(reason: SessionEndReason) {
        val now = System.currentTimeMillis()
        finalizePreviousApp(now)

        // Capture state BEFORE clearing
        val active: ActiveSession? = sessionState.activeSession.value
        val stats: SessionStats = sessionStats.stats.value

        // Write to Firestore (best-effort, doesn't block)
        if (active != null) {
            val doc = buildSessionDoc(active, stats, now, reason)
            sessionSummaryRepo.saveSession(doc)
        }

        // Now clear local state
        monitorJob?.cancel()
        sessionState.endSession(reason)
        sessionStats.reset()
        interventionEngine.onSessionEnded()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("SessionMonitor: stopped ($reason)")
    }

    private fun buildSessionDoc(
        active: ActiveSession,
        stats: SessionStats,
        endedAtMillis: Long,
        reason: SessionEndReason,
    ): SessionDoc {
        val durationSeconds = (endedAtMillis - active.startedAtMillis) / 1000
        val plannedSeconds = active.plannedDurationMinutes * 60L
        val typeString = when (active.sessionType) {
            is SessionType.Timed -> "TIMED"
            SessionType.OpenEnded -> "OPEN_ENDED"
        }
        return SessionDoc(
            sessionId = active.sessionId,
            studentUid = active.studentUid,
            startedAt = Timestamp(active.startedAtMillis / 1000, 0),
            endedAt = Timestamp(endedAtMillis / 1000, 0),
            durationSeconds = durationSeconds,
            plannedDurationSeconds = plannedSeconds,
            sessionType = typeString,
            endReason = reason.name,
            distractionCount = stats.distractionCount,
            distractions = stats.distractionEvents.map {
                DistractionRecord(
                    packageName = it.packageName,
                    category = it.category.name,
                    dwellSeconds = it.dwellSeconds,
                )
            },
            nudgeCount = stats.nudgeCount,
            nagCount = stats.nagCount,
            interventions = stats.interventions,
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, SessionMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_session_active_title))
            .setContentText(getString(R.string.notification_session_active_body))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, getString(R.string.notification_action_end), stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_session),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_session_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        scope.cancel()
    }
}

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
import app.invigilator.core.session.AppCategory
import app.invigilator.core.session.DistractionClassifier
import app.invigilator.core.session.DistractionEvent
import app.invigilator.core.session.SessionStateRepository
import app.invigilator.core.session.SessionStatsRepository
import app.invigilator.feature.blocker.R
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var notificationManager: NotificationManager

    private var currentApp: String? = null
    private var currentAppCategory: AppCategory = AppCategory.UNKNOWN
    private var currentAppEnteredAt: Long = 0L

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "session_monitor"
        private const val POLL_INTERVAL_MS = 2000L
        private const val DISTRACTION_DWELL_THRESHOLD_MS = 30_000L
        const val ACTION_STOP = "app.invigilator.action.STOP_SESSION"
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
        startForeground(NOTIFICATION_ID, buildNotification())
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        sessionStats.reset()
        currentApp = null
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                detectForegroundApp()
                delay(POLL_INTERVAL_MS)
            }
        }
        Timber.d("SessionMonitor: started polling")
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

    private fun stopMonitoring() {
        finalizePreviousApp(System.currentTimeMillis())
        monitorJob?.cancel()
        sessionState.endSession()
        sessionStats.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("SessionMonitor: stopped")
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

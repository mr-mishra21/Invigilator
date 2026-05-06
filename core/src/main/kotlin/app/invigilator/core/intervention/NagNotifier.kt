package app.invigilator.core.intervention

// Manual verification only — see Phase 3 stop gate.

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts and clears the visible nag notification when the user has been
 * on a distractor app for too long. Locale-aware text. Does not check
 * permissions itself — caller (InterventionEngine via the service) is
 * responsible for ensuring POST_NOTIFICATIONS is granted.
 *
 * Notification ID is constant so re-posts replace the existing nag
 * rather than stack.
 */
@Singleton
class NagNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "invigilator_nag"
        const val NOTIFICATION_ID = 2_001
        const val ACTION_END_SESSION = "app.invigilator.ACTION_END_SESSION"
        const val ACTION_REFOCUS = "app.invigilator.ACTION_REFOCUS"
    }

    /**
     * Create the notification channel. Safe to call multiple times.
     * Should be called from Application.onCreate().
     */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(app.invigilator.core.R.string.notif_channel_nag_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(app.invigilator.core.R.string.notif_channel_nag_desc)
            setShowBadge(true)
        }
        mgr.createNotificationChannel(channel)
        Timber.d("NagNotifier: channel created")
    }

    /**
     * Post the nag notification. Locale strings come from resource files;
     * the system picks the right locale via Android's resource resolution.
     *
     * @param distractorAppName the user-visible name of the app, e.g. "YouTube"
     * @param dwellMinutes      how many minutes the user has been on it
     */
    fun postNag(distractorAppName: String, dwellMinutes: Int) {
        val title = context.getString(app.invigilator.core.R.string.nag_title)
        val body = context.getString(
            app.invigilator.core.R.string.nag_body_format,
            distractorAppName,
            dwellMinutes,
        )

        val refocusIntent = Intent(ACTION_REFOCUS).apply { setPackage(context.packageName) }
        val refocusPending = PendingIntent.getBroadcast(
            context, 0, refocusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val endIntent = Intent(ACTION_END_SESSION).apply { setPackage(context.packageName) }
        val endPending = PendingIntent.getBroadcast(
            context, 1, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPending = if (contentIntent != null) {
            PendingIntent.getActivity(
                context, 2, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else null

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setOngoing(false)
            .addAction(0, context.getString(app.invigilator.core.R.string.nag_action_refocus), refocusPending)
            .addAction(0, context.getString(app.invigilator.core.R.string.nag_action_end_session), endPending)
            .apply { if (contentPending != null) setContentIntent(contentPending) }
            .build()

        try {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            mgr.notify(NOTIFICATION_ID, notification)
            Timber.d("NagNotifier: posted nag — $distractorAppName for ${dwellMinutes}m")
        } catch (e: SecurityException) {
            Timber.w(e, "NagNotifier: notification denied (no permission)")
        }
    }

    /** Dismiss the nag notification if showing. */
    fun cancelNag() {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.cancel(NOTIFICATION_ID)
    }
}

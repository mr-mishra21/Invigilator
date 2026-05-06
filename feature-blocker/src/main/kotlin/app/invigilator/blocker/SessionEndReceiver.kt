package app.invigilator.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.invigilator.core.intervention.NagNotifier
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives ACTION_END_SESSION (from the nag notification's "End session"
 * action) and ACTION_REFOCUS (from the "Refocus" action). End session
 * routes to the SessionMonitorService to actually end. Refocus just
 * dismisses the notification.
 *
 * Registered in AndroidManifest.xml.
 */
@AndroidEntryPoint
class SessionEndReceiver : BroadcastReceiver() {

    @Inject lateinit var nagNotifier: NagNotifier

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NagNotifier.ACTION_END_SESSION -> {
                Timber.d("SessionEndReceiver: end session requested from notification")
                nagNotifier.cancelNag()
                val serviceIntent = Intent(context, SessionMonitorService::class.java).apply {
                    action = SessionMonitorService.ACTION_STOP_FROM_NAG
                }
                context.startService(serviceIntent)
            }
            NagNotifier.ACTION_REFOCUS -> {
                Timber.d("SessionEndReceiver: refocus requested from notification")
                nagNotifier.cancelNag()
                // No service action — student stays on current app, session continues
            }
        }
    }
}

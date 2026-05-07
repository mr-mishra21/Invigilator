package app.invigilator.blocker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the device battery level and produces structured Logcat output
 * for off-device analysis. Tagged "BATTERY_DIAG" so post-test extraction
 * is one grep command:
 *   adb logcat | grep BATTERY_DIAG > battery_test.log
 *
 * Called from SessionMonitorService's polling loop every ~30s. Cheap
 * operation — ACTION_BATTERY_CHANGED is a sticky broadcast; reading it
 * via registerReceiver(null, ...) does not register a receiver and does
 * not wake the radio.
 *
 * This instrumentation is permanent low-overhead telemetry, not temporary
 * debug code. It stays in the codebase across all future sprints.
 */
@Singleton
class BatteryDiagnostic @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Returns current battery level as a percentage (0–100), or -1 if
     * the sticky broadcast is unavailable.
     */
    fun currentBatteryPercent(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale)
    }

    /**
     * Emits one diagnostic snapshot. Output format is grep/cut-parseable:
     *   BATTERY_DIAG: t=120s battery=94 fg=com.google.android.youtube cat=DISTRACTING level=NUDGED_ONCE
     */
    fun logSnapshot(
        sessionElapsedSeconds: Long,
        foregroundPackage: String,
        category: String,
        interventionLevel: String,
    ) {
        val battery = currentBatteryPercent()
        Timber.tag("BATTERY_DIAG")
            .d("t=${sessionElapsedSeconds}s battery=$battery fg=$foregroundPackage cat=$category level=$interventionLevel")
    }
}

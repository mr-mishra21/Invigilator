package app.invigilator.blocker

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class BatteryDiagnosticTest {

    private val context: Context = mockk()

    private lateinit var diagnostic: BatteryDiagnostic

    // Captures BATTERY_DIAG-tagged messages from Timber.
    private val logMessages = mutableListOf<String>()
    private val testTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (tag == "BATTERY_DIAG") logMessages.add(message)
        }
    }

    @Before
    fun setUp() {
        diagnostic = BatteryDiagnostic(context)
        Timber.plant(testTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(testTree)
        logMessages.clear()
    }

    @Test
    fun currentBatteryPercent_returns_minus_one_when_receiver_returns_null() {
        every { context.registerReceiver(null, any()) } returns null

        assertEquals(-1, diagnostic.currentBatteryPercent())
    }

    @Test
    fun currentBatteryPercent_returns_valid_percentage_from_intent() {
        val batteryIntent = mockk<Intent> {
            every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 72
            every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        }
        every { context.registerReceiver(null, any()) } returns batteryIntent

        assertEquals(72, diagnostic.currentBatteryPercent())
    }

    @Test
    fun logSnapshot_emits_BATTERY_DIAG_tagged_log_with_all_fields() {
        every { context.registerReceiver(null, any()) } returns null  // battery=-1 in output

        diagnostic.logSnapshot(
            sessionElapsedSeconds = 120L,
            foregroundPackage = "com.google.android.youtube",
            category = "DISTRACTING",
            interventionLevel = "NUDGED_ONCE",
        )

        assertEquals(1, logMessages.size)
        val msg = logMessages[0]
        assertTrue("missing t=", "t=120s" in msg)
        assertTrue("missing fg=", "fg=com.google.android.youtube" in msg)
        assertTrue("missing cat=", "cat=DISTRACTING" in msg)
        assertTrue("missing level=", "level=NUDGED_ONCE" in msg)
    }
}

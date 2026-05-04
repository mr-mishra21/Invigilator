package app.invigilator.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsRepositoryImplTest {

    private val repo = SessionStatsRepositoryImpl()

    private fun makeEvent(pkg: String = "com.instagram.android", dwellSeconds: Long = 60) =
        DistractionEvent(
            packageName = pkg,
            category = AppCategory.DISTRACTING,
            enteredAtMillis = 1000L,
            exitedAtMillis = 1000L + dwellSeconds * 1000,
            dwellSeconds = dwellSeconds,
        )

    @Test
    fun initial_stats_are_zero() {
        assertEquals(0, repo.stats.value.distractionCount)
        assertTrue(repo.stats.value.distractionEvents.isEmpty())
    }

    @Test
    fun recordEvent_increments_count() {
        repo.recordDistractionEvent(makeEvent())
        assertEquals(1, repo.stats.value.distractionCount)
        assertEquals(1, repo.stats.value.distractionEvents.size)
    }

    @Test
    fun multiple_events_accumulate() {
        repo.recordDistractionEvent(makeEvent("com.instagram.android"))
        repo.recordDistractionEvent(makeEvent("com.google.android.youtube"))
        repo.recordDistractionEvent(makeEvent("com.whatsapp"))
        assertEquals(3, repo.stats.value.distractionCount)
    }

    @Test
    fun reset_clears_all_events() {
        repo.recordDistractionEvent(makeEvent())
        repo.recordDistractionEvent(makeEvent())
        repo.reset()
        assertEquals(0, repo.stats.value.distractionCount)
        assertTrue(repo.stats.value.distractionEvents.isEmpty())
    }
}

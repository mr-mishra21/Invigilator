package app.invigilator.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun makeIntervention(type: String, pkg: String = "com.example.app") =
    InterventionRecord(packageName = pkg, type = type, atSecondsIntoSession = 60L)

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

    @Test
    fun recordIntervention_adds_record_to_stats_flow() {
        repo.recordIntervention(makeIntervention("FIRST_NUDGE"))
        assertEquals(1, repo.stats.value.interventions.size)
        assertEquals("FIRST_NUDGE", repo.stats.value.interventions[0].type)
    }

    @Test
    fun recordIntervention_under_50_events_keeps_all() {
        repeat(49) { repo.recordIntervention(makeIntervention("FIRST_NUDGE")) }
        assertEquals(49, repo.stats.value.interventions.size)
    }

    @Test
    fun recordIntervention_at_50_events_caps_correctly() {
        repeat(60) { i ->
            repo.recordIntervention(makeIntervention("FIRST_NUDGE", "pkg_$i"))
        }
        val interventions = repo.stats.value.interventions
        assertEquals(50, interventions.size)
        // takeLast(50) keeps the most recent — last 50 of 60 means pkg_10 through pkg_59
        assertEquals("pkg_10", interventions[0].packageName)
        assertEquals("pkg_59", interventions[49].packageName)
    }

    @Test
    fun nudgeCount_counts_FIRST_AND_SECOND_NUDGES() {
        repo.recordIntervention(makeIntervention("FIRST_NUDGE"))
        repo.recordIntervention(makeIntervention("SECOND_NUDGE"))
        repo.recordIntervention(makeIntervention("NAG"))
        assertEquals(2, repo.stats.value.nudgeCount)
    }

    @Test
    fun nagCount_counts_only_NAG_events() {
        repo.recordIntervention(makeIntervention("FIRST_NUDGE"))
        repo.recordIntervention(makeIntervention("NAG"))
        repo.recordIntervention(makeIntervention("NAG"))
        assertEquals(2, repo.stats.value.nagCount)
    }

    @Test
    fun reset_clears_interventions_list() {
        repo.recordIntervention(makeIntervention("FIRST_NUDGE"))
        repo.recordIntervention(makeIntervention("NAG"))
        repo.reset()
        assertTrue(repo.stats.value.interventions.isEmpty())
        assertEquals(0, repo.stats.value.nudgeCount)
        assertEquals(0, repo.stats.value.nagCount)
    }
}

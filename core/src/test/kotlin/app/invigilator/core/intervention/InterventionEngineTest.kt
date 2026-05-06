package app.invigilator.core.intervention

import app.invigilator.core.session.AppCategory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterventionEngineTest {

    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val phraseLibrary: NudgePhraseLibrary = mockk()
    private val languageRepo: AppLanguageRepository = mockk()

    private lateinit var engine: InterventionEngine

    private val pkg = "com.example.distractor"

    @Before
    fun setUp() {
        coEvery { languageRepo.currentLanguage() } returns AppLanguage.ENGLISH
        coEvery { languageRepo.isTtsAvailable(AppLanguage.ENGLISH) } returns true
        every { phraseLibrary.phrase(any(), any()) } returns "test phrase"
        engine = InterventionEngine(ttsManager, phraseLibrary, languageRepo)
    }

    @Test
    fun `onAppDwellTick below 30s returns NONE`() = runTest {
        engine.scope = this
        val level = engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 10L)
        advanceUntilIdle()
        assertEquals(InterventionLevel.NONE, level)
    }

    @Test
    fun `onAppDwellTick 30s returns DISTRACTION_RECORDED no speak`() = runTest {
        engine.scope = this
        val level = engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 30L)
        advanceUntilIdle()
        assertEquals(InterventionLevel.DISTRACTION_RECORDED, level)
        verify(exactly = 0) { ttsManager.speak(any(), any()) }
    }

    @Test
    fun `onAppDwellTick 60s returns NUDGED_ONCE and calls speak once`() = runTest {
        engine.scope = this
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 30L)
        val level = engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        advanceUntilIdle()
        assertEquals(InterventionLevel.NUDGED_ONCE, level)
        verify(exactly = 1) { ttsManager.speak(any(), AppLanguage.ENGLISH) }
    }

    @Test
    fun `onAppDwellTick 120s returns NUDGED_TWICE and calls speak again`() = runTest {
        engine.scope = this
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        val level = engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 120L)
        advanceUntilIdle()
        assertEquals(InterventionLevel.NUDGED_TWICE, level)
        verify(exactly = 2) { ttsManager.speak(any(), AppLanguage.ENGLISH) }
    }

    @Test
    fun `onAppDwellTick 180s returns NAGGED and calls speak with NAG level`() = runTest {
        engine.scope = this
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 120L)
        val level = engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 180L)
        advanceUntilIdle()
        assertEquals(InterventionLevel.NAGGED, level)
        verify(exactly = 3) { ttsManager.speak(any(), AppLanguage.ENGLISH) }
        // Rotation retries mean phrase() may be called up to 3 times for NAG level
        verify(atLeast = 1) { phraseLibrary.phrase(AppLanguage.ENGLISH, NudgePhraseLibrary.Level.NAG) }
    }

    @Test
    fun `onAppDwellTick app change resets to NONE`() = runTest {
        engine.scope = this
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        advanceUntilIdle()

        val level = engine.onAppDwellTick("com.example.other", AppCategory.DISTRACTING, 5L)
        assertEquals(InterventionLevel.NONE, level)
    }

    @Test
    fun `onAppDwellTick essential apps never trigger interventions`() = runTest {
        engine.scope = this
        val level = engine.onAppDwellTick(pkg, AppCategory.ESSENTIAL, 180L)
        advanceUntilIdle()
        assertEquals(InterventionLevel.NONE, level)
        verify(exactly = 0) { ttsManager.speak(any(), any()) }
    }

    @Test
    fun `onAppDwellTick study apps never trigger interventions`() = runTest {
        engine.scope = this
        val level = engine.onAppDwellTick(pkg, AppCategory.STUDY, 180L)
        advanceUntilIdle()
        assertEquals(InterventionLevel.NONE, level)
        verify(exactly = 0) { ttsManager.speak(any(), any()) }
    }

    @Test
    fun `onAppDwellTick idempotent same input twice no double speak`() = runTest {
        engine.scope = this
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        advanceUntilIdle()
        verify(exactly = 1) { ttsManager.speak(any(), AppLanguage.ENGLISH) }
    }

    @Test
    fun `onSessionEnded resets state for next session`() = runTest {
        engine.scope = this
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        advanceUntilIdle()
        engine.onSessionEnded()

        // Same package, same dwell — state reset means it escalates again
        val level = engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        advanceUntilIdle()
        assertEquals(InterventionLevel.NUDGED_ONCE, level)
        verify(exactly = 2) { ttsManager.speak(any(), AppLanguage.ENGLISH) }
    }

    @Test
    fun `tts unavailable skips speak silently`() = runTest {
        engine.scope = this
        coEvery { languageRepo.isTtsAvailable(AppLanguage.ENGLISH) } returns false
        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        advanceUntilIdle()
        verify(exactly = 0) { ttsManager.speak(any(), any()) }
    }

    @Test
    fun `phrase rotation avoids repeating last phrase`() = runTest {
        engine.scope = this
        val phrase1 = "phrase one"
        val phrase2 = "phrase two"

        // At 60s: first call returns phrase1, which is accepted (no previous phrase)
        every {
            phraseLibrary.phrase(AppLanguage.ENGLISH, NudgePhraseLibrary.Level.FIRST_NUDGE)
        } returns phrase1

        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 60L)
        advanceUntilIdle()
        // lastPhraseSpoken is now phrase1

        // At 120s: first pick returns phrase1 again (same as last) → retry → returns phrase2
        every {
            phraseLibrary.phrase(AppLanguage.ENGLISH, NudgePhraseLibrary.Level.SECOND_NUDGE)
        } returnsMany listOf(phrase1, phrase2)

        engine.onAppDwellTick(pkg, AppCategory.DISTRACTING, 120L)
        advanceUntilIdle()

        // Verify the second speak used phrase2 (rotation worked)
        verify(exactly = 1) { ttsManager.speak(phrase1, AppLanguage.ENGLISH) }
        verify(exactly = 1) { ttsManager.speak(phrase2, AppLanguage.ENGLISH) }
    }
}

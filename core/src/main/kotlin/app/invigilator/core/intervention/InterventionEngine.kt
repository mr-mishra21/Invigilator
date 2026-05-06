package app.invigilator.core.intervention

import app.invigilator.core.session.AppCategory
import app.invigilator.core.util.AppNameResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the per-app-visit intervention escalation state machine. Called
 * by SessionMonitorService on every dwell tick. Decides when to fire
 * voice nudges (now) and visible nags (Phase 3).
 *
 * Per-app: the state resets when the user switches to a different
 * package. A 90-second cumulative tour of Instagram → YouTube →
 * Instagram never escalates past NUDGED_ONCE because each visit's
 * own clock starts fresh.
 *
 * Thread-safety: This class is called from a background coroutine.
 * State is mutable but accessed only from that single coroutine.
 * No locks needed.
 */
@Singleton
class InterventionEngine @Inject constructor(
    private val ttsManager: TtsManager,
    private val phraseLibrary: NudgePhraseLibrary,
    private val languageRepo: AppLanguageRepository,
    private val nagNotifier: NagNotifier,
    private val appNameResolver: AppNameResolver,
) {
    // Internal so tests can substitute a TestScope for deterministic coroutine execution.
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // State for the CURRENT app visit. Reset on app change or session end.
    private var currentPackageName: String? = null
    private var currentLevel: InterventionLevel = InterventionLevel.NONE
    private var lastPhraseSpoken: String? = null

    /**
     * Called every poll tick by SessionMonitorService. Idempotent —
     * same input twice in a row produces same output once. Returns the
     * current intervention level after processing.
     *
     * @param packageName the foreground app's package name
     * @param category    the classifier's category for this package
     * @param dwellSeconds how long the user has been on this app this visit
     */
    fun onAppDwellTick(
        packageName: String,
        category: AppCategory,
        dwellSeconds: Long,
    ): InterventionLevel {
        // Detect app change: reset state for new visit.
        if (packageName != currentPackageName) {
            if (currentPackageName != null) {
                Timber.d("InterventionEngine: app change $currentPackageName → $packageName, resetting level from $currentLevel")
                if (currentLevel == InterventionLevel.NAGGED) {
                    nagNotifier.cancelNag()
                }
            }
            currentPackageName = packageName
            currentLevel = InterventionLevel.NONE
            lastPhraseSpoken = null
        }

        // Only distractors and unknowns can trigger interventions.
        if (category != AppCategory.DISTRACTING && category != AppCategory.UNKNOWN) {
            return currentLevel
        }

        // Compute the level this dwell time should be at.
        val targetLevel = when {
            dwellSeconds >= 180L -> InterventionLevel.NAGGED
            dwellSeconds >= 120L -> InterventionLevel.NUDGED_TWICE
            dwellSeconds >= 60L  -> InterventionLevel.NUDGED_ONCE
            dwellSeconds >= 30L  -> InterventionLevel.DISTRACTION_RECORDED
            else                 -> InterventionLevel.NONE
        }

        // Only act on FORWARD transitions.
        if (targetLevel.ordinal <= currentLevel.ordinal) {
            return currentLevel
        }

        val previousLevel = currentLevel
        currentLevel = targetLevel
        Timber.d("InterventionEngine: $packageName $previousLevel → $targetLevel at ${dwellSeconds}s")

        // Fire side effects for this transition.
        when (targetLevel) {
            InterventionLevel.NUDGED_ONCE -> fireNudge(NudgePhraseLibrary.Level.FIRST_NUDGE)
            InterventionLevel.NUDGED_TWICE -> fireNudge(NudgePhraseLibrary.Level.SECOND_NUDGE)
            InterventionLevel.NAGGED -> {
                // Voice still fires for audio continuity with previous nudges.
                fireNudge(NudgePhraseLibrary.Level.NAG)
                // AND post the visible notification.
                val displayName = appNameResolver.resolveDisplayName(packageName)
                val dwellMinutes = (dwellSeconds / 60).toInt()
                nagNotifier.postNag(displayName, dwellMinutes)
            }
            InterventionLevel.NONE,
            InterventionLevel.DISTRACTION_RECORDED -> { /* no audio side-effect */ }
        }

        return currentLevel
    }

    /**
     * Called by SessionMonitorService when the session ends. Resets
     * all per-visit state so a new session starts clean.
     */
    fun onSessionEnded() {
        Timber.d("InterventionEngine: session ended, resetting state")
        nagNotifier.cancelNag()
        currentPackageName = null
        currentLevel = InterventionLevel.NONE
        lastPhraseSpoken = null
    }

    /**
     * Speaks a phrase in the user's language, picking from the library
     * and avoiding the most-recently-spoken phrase (so the same line
     * doesn't fire back-to-back).
     */
    private fun fireNudge(level: NudgePhraseLibrary.Level) {
        scope.launch {
            val language = languageRepo.currentLanguage()
            val isAvailable = languageRepo.isTtsAvailable(language)
            if (!isAvailable) {
                Timber.d("InterventionEngine: TTS for ${language.tag} unavailable; voice skipped")
                return@launch
            }

            // Try up to 3 picks to avoid repeating the previous phrase.
            // If the library is small and we keep getting the same one,
            // accept it after 3 tries.
            var phrase: String = phraseLibrary.phrase(language, level)
            var attempts = 1
            while (phrase == lastPhraseSpoken && attempts < 3) {
                phrase = phraseLibrary.phrase(language, level)
                attempts++
            }
            lastPhraseSpoken = phrase

            Timber.d("InterventionEngine: speaking phrase (${language.tag}, $level): $phrase")
            ttsManager.speak(phrase, language)
        }
    }
}

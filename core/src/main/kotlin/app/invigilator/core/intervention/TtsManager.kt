package app.invigilator.core.intervention

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Wraps Android's TextToSpeech engine. Handles per-language availability
 * detection at startup and exposes a simple speak() API. Failures are
 * logged but do not throw — TTS is best-effort.
 *
 * Lifecycle: initialize() called once from Application.onCreate.
 * shutdown() called on app process exit (rarely needed; Android cleans up).
 */
@Singleton
class TtsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val languageRepo: AppLanguageRepository,
) {
    private var tts: TextToSpeech? = null
    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Initialize the TTS engine and probe each AppLanguage for availability.
     * Stores results in AppLanguageRepository.
     * Idempotent — calling twice is a no-op.
     */
    fun initialize() {
        if (initialized.getAndSet(true)) return

        scope.launch {
            val engine = createTtsEngine() ?: run {
                Timber.w("TtsManager: TTS engine init failed; voice features disabled")
                AppLanguage.entries.forEach {
                    languageRepo.setTtsAvailability(it, false)
                }
                return@launch
            }
            tts = engine
            probeAvailability(engine)
        }
    }

    private suspend fun createTtsEngine(): TextToSpeech? = suspendCancellableCoroutine { cont ->
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                cont.resume(instance)
            } else {
                Timber.w("TtsManager: init failed with status=$status")
                cont.resume(null)
            }
        }
    }

    private suspend fun probeAvailability(engine: TextToSpeech) {
        AppLanguage.entries.forEach { lang ->
            val locale = Locale.forLanguageTag(lang.tag)
            val result = engine.isLanguageAvailable(locale)
            val available = result == TextToSpeech.LANG_AVAILABLE ||
                            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            Timber.d("TtsManager: ${lang.tag} availability = $result (available=$available)")
            languageRepo.setTtsAvailability(lang, available)
        }
    }

    /**
     * Speak the given text in the given language. Returns immediately;
     * speech happens asynchronously. If TTS is not initialized or the
     * language is unavailable, no-ops silently (caller should check
     * AppLanguageRepository.isTtsAvailable before relying on this).
     */
    fun speak(text: String, language: AppLanguage) {
        val engine = tts
        if (engine == null) {
            Timber.d("TtsManager: speak() called before init complete; ignored")
            return
        }
        val locale = Locale.forLanguageTag(language.tag)
        val setResult = engine.setLanguage(locale)
        if (setResult == TextToSpeech.LANG_MISSING_DATA ||
            setResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Timber.w("TtsManager: setLanguage(${language.tag}) failed: $setResult")
            return
        }
        // QUEUE_FLUSH — interrupt any in-progress speech (a fresh nudge
        // wins over a stale one). Utterance ID lets us track if needed.
        val utteranceId = "nudge_${System.currentTimeMillis()}"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun shutdown() {
        scope.launch {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}

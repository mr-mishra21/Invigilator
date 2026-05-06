package app.invigilator.core.intervention

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Phrases spoken or displayed during interventions.
 *
 * Tone: warm coach, not surveillance. First-nudge phrases are gentle.
 * Second-nudge phrases are slightly more direct. Nag phrases (used for
 * the visible notification in Phase 3) are direct but not punitive.
 *
 * All non-English phrases are [DRAFT] and need native-speaker review
 * before pilot. Tracked in TODO_BACKLOG.md.
 */
@Suppress("HardCodedStringLiteral")
@Singleton
class NudgePhraseLibrary @Inject constructor() {

    enum class Level { FIRST_NUDGE, SECOND_NUDGE, NAG }

    /** Returns a random phrase for the given language and level. */
    fun phrase(language: AppLanguage, level: Level): String {
        val list = library[language]?.get(level) ?: emptyList()
        if (list.isEmpty()) return library[AppLanguage.ENGLISH]!![level]!!.first()
        return list.random(Random.Default)
    }

    private val library: Map<AppLanguage, Map<Level, List<String>>> = mapOf(
        AppLanguage.ENGLISH to mapOf(
            Level.FIRST_NUDGE to listOf(
                "Are you on a break, or back to studying?",
                "A minute on this app — back to focus?",
                "Time to refocus?",
            ),
            Level.SECOND_NUDGE to listOf(
                "It's been a couple of minutes — back to your study session.",
                "This is a longer break than planned. Refocus?",
                "Your focus session is still running — return when ready.",
            ),
            Level.NAG to listOf(
                "You've been on this app for a while. Refocus or end your session.",
                "Long break detected. Pick refocus or end your session below.",
                "Your study session is paused in spirit. Refocus or end?",
            ),
        ),

        // [DRAFT] — needs native Hindi speaker review before pilot
        AppLanguage.HINDI to mapOf(
            Level.FIRST_NUDGE to listOf(
                "क्या आप ब्रेक पर हैं, या पढ़ाई पर वापस?",
                "एक मिनट हो गया — फोकस पर वापस आएं?",
                "वापस फोकस करने का समय?",
            ),
            Level.SECOND_NUDGE to listOf(
                "कुछ मिनट हो गए — अपने स्टडी सेशन पर वापस आएं।",
                "यह सोचे हुए ब्रेक से लंबा हो रहा है। फिर से फोकस करें?",
                "आपका फोकस सेशन अभी भी चल रहा है — तैयार होने पर वापस आएं।",
            ),
            Level.NAG to listOf(
                "आप काफी देर से इस ऐप पर हैं। फोकस करें या सेशन समाप्त करें।",
                "लंबा ब्रेक मिला। नीचे से रिफोकस या सेशन समाप्त करें।",
                "आपका स्टडी सेशन रुक गया है। फिर से फोकस करें या समाप्त करें?",
            ),
        ),

        // [DRAFT] — needs native Assamese speaker review before pilot
        AppLanguage.ASSAMESE to mapOf(
            Level.FIRST_NUDGE to listOf(
                "আপুনি বিৰতিত আছে নে, পঢ়ালৈ ঘূৰি আহিব?",
                "এই এপত এক মিনিট — মনোযোগলৈ ঘূৰি আহিব?",
                "পুনৰ মনোযোগ দিয়াৰ সময়?",
            ),
            Level.SECOND_NUDGE to listOf(
                "কেইটামান মিনিট হ'ল — আপোনাৰ অধ্যয়ন চেচনলৈ ঘূৰি আহক।",
                "এইটো পৰিকল্পনাতকৈ দীঘল বিৰতি হৈছে। পুনৰ মনোযোগ দিব?",
                "আপোনাৰ মনোযোগ চেচন এতিয়াও চলি আছে — যেতিয়া সাজু হ'ব ঘূৰি আহিব।",
            ),
            Level.NAG to listOf(
                "আপুনি এই এপটো বহু সময়ৰ পৰা চলাইছে। পুনৰ মনোযোগ দিয়ক বা চেচন শেষ কৰক।",
                "দীঘল বিৰতি ধৰা পৰিল। তলৰ পৰা পুনৰ মনোযোগ বা চেচন শেষ কৰক।",
                "আপোনাৰ অধ্যয়ন চেচন স্থগিত হৈছে। পুনৰ মনোযোগ বা শেষ কৰিব?",
            ),
        ),

        // [DRAFT] — needs native Bengali speaker review before pilot
        AppLanguage.BENGALI to mapOf(
            Level.FIRST_NUDGE to listOf(
                "আপনি কি বিরতিতে আছেন, নাকি পড়াশোনায় ফিরবেন?",
                "এই অ্যাপে এক মিনিট — মনোযোগে ফিরবেন?",
                "আবার মনোযোগ দেওয়ার সময়?",
            ),
            Level.SECOND_NUDGE to listOf(
                "কয়েক মিনিট হয়ে গেছে — আপনার স্টাডি সেশনে ফিরে আসুন।",
                "এটি পরিকল্পনার চেয়ে দীর্ঘ বিরতি। আবার মনোযোগ দেবেন?",
                "আপনার ফোকাস সেশন এখনও চলছে — প্রস্তুত হলে ফিরে আসুন।",
            ),
            Level.NAG to listOf(
                "আপনি বেশ কিছুক্ষণ ধরে এই অ্যাপে আছেন। মনোযোগ দিন বা সেশন শেষ করুন।",
                "দীর্ঘ বিরতি শনাক্ত হয়েছে। নিচে থেকে রিফোকাস বা সেশন শেষ করুন।",
                "আপনার স্টাডি সেশন থেমে আছে। আবার মনোযোগ দেবেন নাকি শেষ করবেন?",
            ),
        ),
    )
}

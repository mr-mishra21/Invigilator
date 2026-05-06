package app.invigilator.core.intervention

/**
 * Languages supported for voice nudges and visible nags.
 * The string value is the BCP-47 language tag used by Android Locale and TTS.
 */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    HINDI("hi"),
    ASSAMESE("as"),
    BENGALI("bn"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull {
            it.tag.equals(tag, ignoreCase = true)
        } ?: ENGLISH

        /** Pick the best AppLanguage for the user's current device locale. */
        fun fromSystemLocale(): AppLanguage {
            val systemTag = java.util.Locale.getDefault().language
            return fromTag(systemTag)
        }
    }
}

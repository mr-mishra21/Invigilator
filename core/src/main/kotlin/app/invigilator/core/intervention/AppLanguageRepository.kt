package app.invigilator.core.intervention

import kotlinx.coroutines.flow.Flow

interface AppLanguageRepository {
    /** Observe the user's currently selected app language. */
    fun observeLanguage(): Flow<AppLanguage>

    /** Get the current language synchronously (for service code). */
    suspend fun currentLanguage(): AppLanguage

    /** Set the user's preferred language. */
    suspend fun setLanguage(lang: AppLanguage)

    /** Set whether TTS is available for a given language on this device. */
    suspend fun setTtsAvailability(lang: AppLanguage, available: Boolean)

    /** Read TTS availability for a given language. */
    suspend fun isTtsAvailable(lang: AppLanguage): Boolean
}

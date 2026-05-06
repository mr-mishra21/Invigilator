package app.invigilator.core.intervention

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AppLanguageRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppLanguageRepository {

    private val languageKey = stringPreferencesKey("app_language")
    private fun ttsAvailKey(lang: AppLanguage) =
        booleanPreferencesKey("tts_available_${lang.tag}")

    override fun observeLanguage(): Flow<AppLanguage> = dataStore.data.map { prefs ->
        AppLanguage.fromTag(prefs[languageKey])
            .also { /* no-op; we don't write here to keep observe pure */ }
    }

    override suspend fun currentLanguage(): AppLanguage {
        val prefs = dataStore.data.first()
        val stored = prefs[languageKey]
        return if (stored == null) {
            val derived = AppLanguage.fromSystemLocale()
            setLanguage(derived)
            derived
        } else {
            AppLanguage.fromTag(stored)
        }
    }

    override suspend fun setLanguage(lang: AppLanguage) {
        dataStore.edit { it[languageKey] = lang.tag }
    }

    override suspend fun setTtsAvailability(lang: AppLanguage, available: Boolean) {
        dataStore.edit { it[ttsAvailKey(lang)] = available }
    }

    override suspend fun isTtsAvailable(lang: AppLanguage): Boolean {
        return dataStore.data.first()[ttsAvailKey(lang)] ?: false
    }
}

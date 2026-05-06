package app.invigilator.core.intervention

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDataStore : DataStore<Preferences> {
    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val newData = transform(_data.value)
        _data.value = newData
        return newData
    }
}

class AppLanguageRepositoryImplTest {

    private fun repo() = AppLanguageRepositoryImpl(FakeDataStore())

    @Test
    fun `currentLanguage returns system-derived language on first call`() = runTest {
        val result = repo().currentLanguage()
        // System locale in JVM tests is typically English
        assertEquals(AppLanguage.fromSystemLocale(), result)
    }

    @Test
    fun `currentLanguage returns stored language on subsequent calls`() = runTest {
        val r = repo()
        r.setLanguage(AppLanguage.HINDI)
        assertEquals(AppLanguage.HINDI, r.currentLanguage())
    }

    @Test
    fun `setLanguage persists and is observable via observeLanguage`() = runTest {
        val r = repo()
        r.setLanguage(AppLanguage.BENGALI)
        assertEquals(AppLanguage.BENGALI, r.observeLanguage().first())
    }

    @Test
    fun `setTtsAvailability and isTtsAvailable round-trip correctly`() = runTest {
        val r = repo()
        assertFalse(r.isTtsAvailable(AppLanguage.HINDI))
        r.setTtsAvailability(AppLanguage.HINDI, true)
        assertTrue(r.isTtsAvailable(AppLanguage.HINDI))
        r.setTtsAvailability(AppLanguage.HINDI, false)
        assertFalse(r.isTtsAvailable(AppLanguage.HINDI))
    }
}

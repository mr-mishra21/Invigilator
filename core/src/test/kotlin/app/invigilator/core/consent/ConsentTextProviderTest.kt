package app.invigilator.core.consent

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

class ConsentTextProviderTest {

    private lateinit var context: Context
    private lateinit var assets: AssetManager
    private lateinit var provider: ConsentTextProvider

    private val englishText = "Invigilator — Student Consent (18+)\n\nConsent version: v1.0"

    @Before
    fun setUp() {
        assets = mockk()
        context = mockk {
            every { assets } returns this@ConsentTextProviderTest.assets
        }
        provider = ConsentTextProvider(context)
    }

    @Test
    fun `resolve returns text and hash for supported language`() {
        every {
            assets.open("consents/AdultStudentSelfConsent_v1.0_en.txt")
        } returns ByteArrayInputStream(englishText.toByteArray(Charsets.UTF_8))

        val result = provider.resolve(ConsentType.ADULT_STUDENT_SELF, "v1.0", "en")

        assertNotNull(result)
        assertEquals(englishText, result!!.text)
        assertEquals(provider.sha256(englishText), result.hash)
    }

    @Test
    fun `resolve falls back to en when language file missing`() {
        every {
            assets.open("consents/AdultStudentSelfConsent_v1.0_fr.txt")
        } throws FileNotFoundException("not found")
        every {
            assets.open("consents/AdultStudentSelfConsent_v1.0_en.txt")
        } returns ByteArrayInputStream(englishText.toByteArray(Charsets.UTF_8))

        val result = provider.resolve(ConsentType.ADULT_STUDENT_SELF, "v1.0", "fr")

        assertNotNull(result)
        assertEquals(englishText, result!!.text)
    }

    @Test
    fun `resolve returns null when neither language nor en file exists`() {
        every {
            assets.open("consents/AdultStudentSelfConsent_v1.0_fr.txt")
        } throws FileNotFoundException("not found")
        every {
            assets.open("consents/AdultStudentSelfConsent_v1.0_en.txt")
        } throws FileNotFoundException("not found")

        val result = provider.resolve(ConsentType.ADULT_STUDENT_SELF, "v1.0", "fr")

        assertNull(result)
    }

    @Test
    fun `sha256 is deterministic`() {
        val h1 = provider.sha256(englishText)
        val h2 = provider.sha256(englishText)
        assertEquals(h1, h2)
    }

    @Test
    fun `sha256 produces 64-char hex string`() {
        val hash = provider.sha256(englishText)
        assertEquals(64, hash.length)
        assert(hash.all { it.isLetterOrDigit() })
    }

    @Test
    fun `sha256 differs for different inputs`() {
        val h1 = provider.sha256("text one")
        val h2 = provider.sha256("text two")
        assert(h1 != h2)
    }
}

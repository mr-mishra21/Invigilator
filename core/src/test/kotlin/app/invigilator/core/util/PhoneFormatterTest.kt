package app.invigilator.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneFormatterTest {

    @Test
    fun `10 digits normalized to E164`() {
        val result = PhoneFormatter.normalize("9876543210")
        assertEquals("+919876543210", result.getOrThrow())
    }

    @Test
    fun `E164 with plus prefix returned as-is`() {
        val result = PhoneFormatter.normalize("+919876543210")
        assertEquals("+919876543210", result.getOrThrow())
    }

    @Test
    fun `E164 with spaces normalized`() {
        val result = PhoneFormatter.normalize("+91 9876 543 210")
        assertEquals("+919876543210", result.getOrThrow())
    }

    @Test
    fun `12 digits with 91 prefix normalized`() {
        val result = PhoneFormatter.normalize("919876543210")
        assertEquals("+919876543210", result.getOrThrow())
    }

    @Test
    fun `invalid number returns failure`() {
        val result = PhoneFormatter.normalize("12345")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}

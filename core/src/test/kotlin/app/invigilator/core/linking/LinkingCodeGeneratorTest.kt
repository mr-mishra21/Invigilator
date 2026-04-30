package app.invigilator.core.linking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LinkingCodeGeneratorTest {

    private fun generateCode(): String = String.format("%06d", Random.nextInt(1_000_000))

    @Test
    fun `code is always exactly 6 characters`() {
        repeat(100) {
            val code = generateCode()
            assertEquals("Expected 6 chars, got '$code'", 6, code.length)
        }
    }

    @Test
    fun `code contains only digits`() {
        repeat(100) {
            val code = generateCode()
            assertTrue("Non-digit in '$code'", code.all { it.isDigit() })
        }
    }

    @Test
    fun `code preserves leading zeros`() {
        val code = String.format("%06d", 5)
        assertEquals("000005", code)
    }

    @Test
    fun `code zero is padded to 000000`() {
        val code = String.format("%06d", 0)
        assertEquals("000000", code)
    }

    @Test
    fun `codes show randomness over 1000 generations`() {
        val codes = (1..1000).map { generateCode() }.toSet()
        assertTrue("Expected > 500 unique codes in 1000, got ${codes.size}", codes.size > 500)
    }
}

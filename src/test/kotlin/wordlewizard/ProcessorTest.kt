package wordlewizard

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.asserter
import wordlewizard.Processor.Companion.Status.*

private fun assertAlmostEquals(expected: Double, actual: Double, threshhold: Double = 1e-5, message: String? = null) {
    asserter.assertTrue(message, abs(expected - actual) < threshhold)
}

class ProcessorTest {
    companion object {
        val p = Processor.fromCandidates("full_list_nyt")
        private const val WEARY = "WEARY"
        val weary_info1 = p.toWordInformation(WEARY, listOf(GREY, GREEN, GREEN, YELLOW, GREY))
        val weary_info2 = p.toWordInformation(WEARY, listOf(GREEN, GREY, GREY, YELLOW, GREEN))
        val weary_info3 = p.toWordInformation(WEARY, listOf(GREY, GREY, GREY, GREY, GREY))
    }

    @Test
    fun `processor has 243 patterns`() {
        assertEquals(243, p.allPatterns.size)
    }

    @Test
    fun `WEARY with pattern XGGYX has 18 candidates`() {
        assertEquals(18, weary_info1.compatibleWords().size)
    }

    @Test
    fun `WEARY with pattern GXXYG has 3 candidates`() {
        assertEquals(3, weary_info2.compatibleWords().size)
    }

    @Test
    fun `WEARY with pattern XXXXX has 1844 candidates`() {
        assertEquals(1844, weary_info3.compatibleWords().size)
    }

    @Test
    fun `WEARY with pattern GXXYG has probability 3 over 12972`() {
        assertEquals(3.0/12_966, weary_info2.p())
    }

    @Test
    fun `WEARY with pattern GXXYG has information approx 12 point 08`() {
        assertAlmostEquals(12.08, weary_info2.info(), 5e-3)
    }

    @Test
    fun `WEARY has an expected information value of 4 point 90 bits`() {
        assertAlmostEquals(4.90, p.entropy(WEARY).second, 5e-3)
    }
}
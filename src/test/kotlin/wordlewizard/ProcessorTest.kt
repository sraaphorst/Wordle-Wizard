package wordlewizard

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.asserter

private fun assertAlmostEquals(expected: Double, actual: Double, threshhold: Double = 1e-5, message: String? = null) {
    asserter.assertTrue(message, abs(expected - actual) < threshhold)
}

class ProcessorTest {
    companion object {
        val p = Processor.fromCandidates("full_list_nyt.txt")

        val weary_info1 = p.toWordInformation(listOf(
                'W' to Processor.Companion.Status.GREY,
                'E' to Processor.Companion.Status.GREEN,
                'A' to Processor.Companion.Status.GREEN,
                'R' to Processor.Companion.Status.YELLOW,
                'Y' to Processor.Companion.Status.GREY
        ))

        val weary_info2 = p.toWordInformation(listOf(
                'W' to Processor.Companion.Status.GREEN,
                'E' to Processor.Companion.Status.GREY,
                'A' to Processor.Companion.Status.GREY,
                'R' to Processor.Companion.Status.YELLOW,
                'Y' to Processor.Companion.Status.GREEN
        ))

        val weary_info3 = p.toWordInformation(listOf(
                'W' to Processor.Companion.Status.GREY,
                'E' to Processor.Companion.Status.GREY,
                'A' to Processor.Companion.Status.GREY,
                'R' to Processor.Companion.Status.GREY,
                'Y' to Processor.Companion.Status.GREY
        ))
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
}
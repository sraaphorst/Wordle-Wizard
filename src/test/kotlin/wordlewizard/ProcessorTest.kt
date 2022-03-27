package wordlewizard

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
}
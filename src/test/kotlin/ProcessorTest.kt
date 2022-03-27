import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProcessorTest {
    companion object {
        val p = Processor.fromCandidates("full_list_nyt.txt")

        val weary_info = p.toWordInformation(listOf(
                'W' to Processor.Companion.Status.GREY,
                'E' to Processor.Companion.Status.GREEN,
                'A' to Processor.Companion.Status.GREEN,
                'R' to Processor.Companion.Status.YELLOW,
                'Y' to Processor.Companion.Status.GREY
        ))
    }
    @Test
    fun `WEARY with pattern XGGYX has 18 candidates`() {
        assertEquals(18, weary_info.compatibleWords().size)
    }
}
// By Sebastian Raaphorst, 2022.

import java.lang.Integer.max
import java.lang.Integer.min


// Convenience type aliases.
typealias LetterCounts = Map<Char, Int>
typealias Frequencies = Map<Int, LetterCounts>

// Convenience functions to:
// 1. Avoid having to handle null when we know they will succeed and just return safe values.
// 2. Intersect IntRanges, which should produce another (possibly empty) IntRange.
//    The default IntRange.intersect method produces a Set<Int>.
private fun <A,B> Map<A, Map<B, Int>>.lookup(a: A, b: B): Int =
        (this[a] ?: emptyMap())[b] ?: 0
private fun <A, B> Map<A, Set<B>>.lookup(a: A): Set<B> =
        this[a] ?: emptySet()
private fun <A> Map<A, Int>.lookup(a: A): Int =
        this[a] ?: 0
private fun <A> Map<A, Boolean>.lookup(a : A): Boolean =
        this[a] ?: false
private fun <A> Map<A, IntRange>.lookup(a: A): IntRange =
        this[a] ?: IntRange.EMPTY
private fun IntRange.intersection(other: IntRange): IntRange =
       max(first, other.first)..min(last, other.last)


class Processor constructor(private val candidateWords: Set<String>) {
    companion object {
        const val letters = "abcdefghijklmnopqrstuvwxyz"

        // I prefer to think in the Wordle colours.
        // BLACK: Not in.
        // GREY: In, but wrong position.
        // GREEN: In, and in right position.
        enum class Status {
            BLACK,
            GREY,
            GREEN
        }
    }

    // Make sure that the candidateWords are all the same length or this does not make sense.
    init {
        require(candidateWords.isNotEmpty()) { "Cannot work with an empty set of candidate words." }
        val sz = candidateWords.fold(Int.MAX_VALUE) { acc, word -> min(acc, word.length) }
        require(sz > 0) { "Empty string found in candidate words." }
        require(candidateWords.all { it.length == sz }) { "All candidate words must have the same size." }
    }

    // Determine the word length of the candidates.
    val n: Int = candidateWords.first().length

    // Set up the frequency map. This just determines, from the list of candidate words, how often each letter appears
    // in each position.
    val frequencies: Frequencies = (0 until n).associateWith {
        pos -> letters.map { ch -> ch to candidateWords.count { word -> word[pos] == ch } }.toMap()
    }

    // Contains known information about the word currently being guessed.
    inner class WordInformation(private val candidates: Map<Int, Set<Char>> = (0..n).associateWith { letters.toSet() },
                                private val counts: Map<Char, IntRange> = letters.associateWith { 0 .. n }) {
        // Determine if the word given is compatible with the information here.
        // This will come in particularly useful if hard mode is on.
        fun isCompatible(word: String): Boolean {
            require(word.length == n) { "The word $word has illegal length ${word.length}, should be $n." }

            // Make sure the letters are suitable candidates for each position.
            return word.withIndex().all { (idx, chr) -> chr in candidates.lookup(idx) } &&
                    // Make sure the count of each letter is n the IntRange for that letter.
                    letters.all { ch -> word.count { it == ch } in counts.lookup(ch) }
        }

        // Determine if this word information specifies a unique word, and if so, return that word.
        fun isWord(): String? = when {
            (candidates.all { it.value.size == 1 }) -> {
                val word = candidates.map { it.value.first() }.toString()
                require(word in candidateWords) { "WordInformation represents word $word, which is not a valid candidate." }
                word
            }
            else -> null
        }

        // Given another WordInformation, find the refinement of the first by the other.
        // Note that this is a commutative operation as it is essentially intersection.
        // It is a shame that the intersection of two IntRanges is not an IntRange.
        fun refine(other: WordInformation): WordInformation =
                WordInformation((0..n).associateWith { candidates.lookup(it).intersect(other.candidates.lookup(it)) },
                        letters.associateWith { counts.lookup(it).intersection(other.counts.lookup(it)) }
                )

        // Get all the compatible words that can be matched by this WordInformation.
        fun compatibleWords(): Set<String> =
                candidateWords.filter(this::isCompatible).toSet()
    }

    // Given a guess, translate it into a WordInformation given what information it provides us.
    // This requires somewhat delicate handling, because we could have something like:
    // 1. E_E_E where one E is green, one is grey, and one is black, which means that there are two Es, and one is in
    //    the correct position, and one is not.
    // 2. E_E__ where one is green and one is grey, which means there are at least two Es, but
    //    there could be more.
    // 3. E_E__ where both are grey, which means there are at least two Es, but both are explicitly not in the position
    //    specified.
    fun toWordInformation(info: List<Pair<Char, Status>>): WordInformation {
        // Calculate the minimum number of times a letter can appear based on the information provided.
        // This is the sum of the times it appears in green and grey.
        val minFrequencies = letters.associateWith { ch -> info.count {
            (ch2, status) -> ch == ch2 && status in setOf(Status.GREEN, Status.GREY) }
        }

        // To calculate a letter's maximum frequency is considerably more complicated.
        // 1. If a letter appears in black, then its max frequency is its min frequency.
        //    This will handle guesses like EMCEE where the second and third Es are black.
        // 2. If a letter does not appear in black, then its maximum frequency is the
        //    minimum frequency + (n - sum of other minimum frequencies).
        // I'm concerned about the calculation in (2): does this account for positions where the letter is not allowed?
        // We will do a pass afterwards on the positions a letter can still fit in and take the min?
        val maxFrequencies = letters.associateWith { ch -> when {
            info.contains(ch to Status.BLACK) -> minFrequencies.lookup(ch)
            else -> minFrequencies.lookup(ch) + (n - minFrequencies.filterNot { it.key != ch }.map { it.value }.sum())
        } }

        // This just determines if a letter has achieved its maximum frequency in the correct positions.
        // It allows for a small correction on the position candidates.
        val maxAchieved = letters.associateWith {
            maxFrequencies.lookup(it) == info.count { (ch, status) -> ch == it && status == Status.GREEN }
        }

        // Calculate, for each position, what letters can appear there.
        // Cases to consider:
        // 1. If a letter is green in that position, it is the only candidate.
        // 2. If the letter is grey or black in that position, then any other letter with a min frequency whose max
        //    frequency has not been achieved can go there.
        val candidates = (0 until n).associateWith { pos ->
            when (info[pos].second) {
                Status.GREEN -> setOf(info[pos].first)
                else -> letters.filter { ch -> ch != info[pos].first && !maxAchieved.lookup(ch) }.toSet()
            }
        }

        // Combine the min / max frequencies into IntRanges.
        val counts = letters.associateWith { minFrequencies.lookup(it)..maxFrequencies.lookup(it) }

        return WordInformation(candidates, counts)
    }

    // Rank a word's score.
    // This is just the sum of the frequencies of each letter at each position.
    // TODO: THIS MAY BE VERY WRONG.
    fun rankWord(word: String): Int {
        require(word.length == n) { "Word $word must be of length $n." }
        return word.withIndex().sumOf { (idx, ch) -> frequencies.lookup(idx, ch) }
    }
}

fun main() {
    // The list of candidate words for consideration.
    val candidateWords = object {}.javaClass.getResource("/full_list_nyt.txt")!!
            .readText().trim().split("\n").toSet()

    // Run the basic processing for now.
    val p = Processor(candidateWords)
    println(p.frequencies)
    val candidateMap = candidateWords.associateBy {
        p.rankWord(it)
    }.toSortedMap(Comparator.reverseOrder())
    println(candidateMap)
}
// By Sebastian Raaphorst, 2022.

import java.lang.Integer.max
import java.lang.Integer.min


// Convenience type aliases.
typealias Word = String
typealias LetterCounts = Map<Char, Int>
typealias Frequencies = Map<Int, LetterCounts>

// Convenience functions to:
// 1. Avoid having to handle null when we know they will succeed and just return safe values.
// 2. Intersect IntRanges, which should produce another (possibly empty) IntRange.
//    The default IntRange.intersect method produces a Set<Int>.
// 3. Check that a string is uppercase or bork.
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
private fun Word.requireUpperCaseWord(msg: () -> String): Boolean {
        require(all { it.isLetter() && it.isUpperCase() }, msg)
        return true
}


class Processor constructor(val candidateWords: List<Word>) {
    companion object {
        fun fromCandidates(filename: String): Processor =
                Processor(object {}.javaClass.getResource(filename)!!.readText().trim().split("\n"))

        val letters = 'A'..'Z'

        // I prefer to think in the Wordle colours.
        // GREY: Not in.
        // YELLOW: In, but wrong position.
        // GREEN: In, and in right position.
        enum class Status {
            GREY,
            YELLOW,
            GREEN
        }
    }

    // Make sure that the candidateWords are all the same length or this does not make sense.
    init {
        require(candidateWords.isNotEmpty()) { "Cannot work with an empty set of candidate words." }
        require(candidateWords.all { it.requireUpperCaseWord { "Illegal characters found in candidate word $it." } })
        val sz = candidateWords.fold(Int.MAX_VALUE) { acc, word -> min(acc, word.length) }
        require(sz > 0) { "Empty string found in candidate words." }
        require(candidateWords.all { it.length == sz }) { "All candidate words must have the same size." }
    }

    // Determine the word length of the candidates.
    val n: Int = candidateWords.first().length

    // Set up the frequency map. This just determines, from the list of candidate words, how often each letter appears
    // in each position.
    val frequencies: Frequencies = (0 until n).associateWith { pos ->
        letters.associateWith { ch -> candidateWords.count { word -> word[pos] == ch } }
    }

    // Contains known information about the words that have been guessed.
    inner class WordInformation constructor(private val candidates: Map<Int, Set<Char>> = (0..n).associateWith { letters.toSet() },
                                            private val counts: Map<Char, IntRange> = letters.associateWith { 0 .. n },
                                            private val guesses: Set<Word> = emptySet()) {
        init {
            require(counts.keys.all { it.isLetter() && it.isUpperCase() }) {
                "Illegal characters found in WordInformation: ${counts.keys.filterNot { it.isLetter() && it.isUpperCase() }}."
            }
            require(candidates.values.flatten().all { it.isLetter() && it.isUpperCase() }) {
                "Illegal characters found in WordInformation: ${candidates.values.flatten().filterNot { it.isLetter() && it.isUpperCase() }}."
            }
            require(guesses.all { it.length == n }) {
                "Illegal length words specified in guesses: ${guesses.filterNot { it.length == n }}."
            }
        }

        // Determine if the word given is compatible with the information here.
        // This will come in particularly useful if hard mode is on.
        fun isCompatible(word: Word): Boolean {
            word.requireUpperCaseWord { "isCompatible passed a word with illegal characters: $word" }
            require(word.length == n) { "The word $word has illegal length ${word.length}, should be $n." }

            // Make sure the letters are suitable candidates for each position.
            return word.withIndex().all { (idx, chr) -> chr in candidates.lookup(idx) } &&
                    // Make sure the count of each letter is n the IntRange for that letter.
                    letters.all { ch -> word.count { it == ch } in counts.lookup(ch) }
        }

        // Determine if this word information specifies a unique word, and if so, return that word.
        fun isWord(): Word? = when {
            (candidates.all { it.value.size == 1 }) -> {
                val word = candidates.map { it.value.first() }.toString()
                word.requireUpperCaseWord { "WordInformation represents non-uppercase word $word." }
                require(word in candidateWords) { "WordInformation represents word $word, which is not a valid candidate." }
                word
            }
            else -> null
        }

        // Given another WordInformation, find the refinement of the first by the other.
        // Note that this is a commutative operation as it is essentially intersection.
        fun refine(other: WordInformation): WordInformation =
                WordInformation((0..n).associateWith { candidates.lookup(it).intersect(other.candidates.lookup(it)) },
                        letters.associateWith { counts.lookup(it).intersection(other.counts.lookup(it)) },
                        guesses + other.guesses
                )

        // Get all the compatible words that can be matched by this WordInformation.
        // Exclude things that have already been guessed.
        fun compatibleWords(): Set<Word> =
                candidateWords.filterNot(guesses::contains).filter(this::isCompatible).toSet()
    }

    // Given a guess, translate it into a WordInformation given what information it provides us.
    // This requires somewhat delicate handling, because we could have something like:
    // 1. E_E_E where one E is green, one is yellow, and one is grey, which means that there are two Es, and one is
    //    in the correct position, and one is not.
    // 2. E_E__ where one is green and one is yellow, which means there are at least two Es, but
    //    there could be more.
    // 3. E_E__ where both are yellow, which means there are at least two Es, but both are explicitly not in the
    //    position specified.
    fun toWordInformation(info: List<Pair<Char, Status>>): WordInformation {
        require(info.all { it.first.isUpperCase() }) {
            "Illegal characters found in toWordInformation: ${info.filter { it.first.isLowerCase() }}"
        }
        require(info.size == n) { "Feedback must be of length $n, was of length ${info.size}." }

        // Extract the original word.
        val wordGuessed = String(info.map(Pair<Char, Status>::first).toCharArray())

        // Calculate the minimum number of times a letter can appear based on the information provided.
        // This is the sum of the times it appears in green and yellow.
        val minFrequencies = letters.associateWith { ch -> info.count {
            (ch2, status) -> ch == ch2 && status in setOf(Status.GREEN, Status.YELLOW) }
        }

        // To calculate a letter's maximum frequency is considerably more complicated.
        // 1. If a letter appears in grey, then its max frequency is its min frequency.
        //    This will handle guesses like EMCEE where the second and third Es are grey.
        // 2. If a letter does not appear in grey, then its maximum frequency is the
        //    minimum frequency + (n - sum of other minimum frequencies).
        // I'm concerned about the calculation in (2): does this account for positions where the letter is not allowed?
        // We will do a pass afterwards on the positions a letter can still fit in and take the min?
        val maxFrequencies = letters.associateWith { ch -> when {
            info.contains(ch to Status.GREY) -> minFrequencies.lookup(ch)
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
        // 2. If the letter is yellow or grey in that position, then any other letter with a min frequency whose max
        //    frequency has not been achieved can go there.
        val candidates = (0 until n).associateWith { pos ->
            when (info[pos].second) {
                Status.GREEN -> setOf(info[pos].first)
                else -> letters.filter { ch -> ch != info[pos].first && !maxAchieved.lookup(ch) }.toSet()
            }
        }

        // Combine the min / max frequencies into IntRanges.
        val counts = letters.associateWith { minFrequencies.lookup(it)..maxFrequencies.lookup(it) }

        return WordInformation(candidates, counts, setOf(wordGuessed))
    }

    // Rank a word's score.
    // This is just the sum of the frequencies of each letter at each position.
    // TODO: THIS MAY BE VERY WRONG.
    fun rankWord(word: Word): Int {
        word.requireUpperCaseWord { "Illegal characters found in rankWord: $word" }
        require(word.length == n) { "Word $word must be of length $n." }
        return word.withIndex().sumOf { (idx, ch) -> frequencies.lookup(idx, ch) }
    }
}

fun main(args: Array<String>) {
    require(args.size in setOf(0, 1)) { "Permitted parameters: answers.txt, answers_nyt.txt, full_list.txt, default=full_list_nyt.txt" }

    // Create the processor.
    val p = Processor.fromCandidates(args.getOrNull(0) ?: "full_list_nyt.txt")
    println(p.frequencies)
    val candidateMap = p.candidateWords.associateBy {
        p.rankWord(it)
    }.toSortedMap(Comparator.reverseOrder())
    println(candidateMap)
}
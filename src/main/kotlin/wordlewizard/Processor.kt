package wordlewizard

// By Sebastian Raaphorst, 2022.

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.URL
import kotlin.Comparator
import kotlin.math.log2
import kotlin.system.measureTimeMillis

// Convenience type aliases.
typealias Word = String
typealias LetterCounts = Map<Char, Int>
typealias Frequencies = Map<Int, LetterCounts>
typealias ExpectedValues = Map<Word, Double>

// Convenience functions to:
// 1. Avoid having to handle null when we know they will succeed and just return safe values.
// 2. Intersect IntRanges, which should produce another (possibly empty) IntRange.
//    The default IntRange.intersect method produces a Set<Int>.
// 3. Check that a string is uppercase or bork.
private fun <A,B> Map<A, Map<B, Int>>.lookup(a: A, b: B): Int =
        (this[a] ?: emptyMap())[b] ?: 0
private fun <A, B> Map<A, Set<B>>.lookup(a: A): Set<B> =
        this[a] ?: emptySet()
private fun <A> Map<A, Double>.lookup(a: A): Double =
        this[a] ?: 0.0
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
        fun fromCandidates(fileArg: String): Processor =
                Processor(object{}.javaClass.getResource("/$fileArg.txt")!!.readText().trim().split("\n"))

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

    // Precompute all the patterns that can be returned for a word.
    // Not tail recursive but this shouldn't matter since n will presumably be small.
    private fun createAllPatterns(): Set<List<Status>> {
        fun aux(curr: List<Status> = emptyList()): Set<List<Status>> = when (curr.size) {
            n -> setOf(curr)
            else -> Status.values().flatMap { aux(curr + listOf(it)) }.toSet()
        }
        return aux()
    }

    // All possible patterns that can be returned for a guess.
    val allPatterns = createAllPatterns()

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
        private fun isCompatible(word: Word): Boolean {
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

        // Calculate the probability associated with this WordInformation,
        // i.e. # candidate words / total number words.
        fun p(): Double =
                compatibleWords().size.toDouble() / candidateWords.size

        // Calculate the information associated with the WordInformation,
        // i.e. -log_2(p).
        // This is because p = (1/2)^info, i.e. the number of times we cut the space in half.
        // Rearranges to info = -log_2(p).
        fun info(): Double {
            val prob = p()
            return if (prob == 0.0) 0.0 else -log2(prob)
        }
    }

    // Given a guess, translate it into a WordInformation given what information it provides us.
    // This requires somewhat delicate handling, because we could have something like:
    // 1. E_E_E where one E is green, one is yellow, and one is grey, which means that there are two Es, and one is
    //    in the correct position, and one is not.
    // 2. E_E__ where one is green and one is yellow, which means there are at least two Es, but
    //    there could be more.
    // 3. E_E__ where both are yellow, which means there are at least two Es, but both are explicitly not in the
    //    position specified.
    fun toWordInformation(word: String, pattern: List<Status>): WordInformation {
        word.requireUpperCaseWord { "Illegal characters found in toWordInformation: $word" }
        require(word.length == n) { "The word $word has illegal length ${word.length}, should be $n." }
        require(pattern.size == n) { "The feedback $pattern has illegal length ${pattern.size}, should be $n." }

        val info = word.toCharArray().zip(pattern)

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

        return WordInformation(candidates, counts, setOf(word))
    }

    // The expected information that a word gives us. Note that this may be a very intensive computation as it
    // has to try all 3^n patterns that a word can return. (When n=5, this is 243.)
    fun expectedInfo(word: Word): Pair<Word, Double> {
        word.requireUpperCaseWord { "Illegal characters found in rankWord: $word" }
        require(word.length == n) { "Word $word must be of length $n." }
        return word to allPatterns.sumOf {
            val wi = toWordInformation(word, it)
            wi.p() * wi.info()
        }
    }
}

// Serialize the expected values of a word for a processor.
// Writes to the resources directory but this hardly seems like the ideal way to do so.
fun serialize(p: Processor, fileArg: String): ExpectedValues = runBlocking(Dispatchers.Default) {
    val infoMap: ExpectedValues = p.candidateWords.map { async { p.expectedInfo(it) } }.awaitAll().toMap()
    val json = Json.encodeToString(infoMap)
    File("src/main/resources/$fileArg.ser").writeText(json)
    return@runBlocking infoMap
}

// Deserialize the expected values from a file in the resources directory.
fun deserialize(fileArg: String): ExpectedValues =
    Json.decodeFromString(object{}.javaClass.getResource("/$fileArg.ser")!!.readText())


fun main(args: Array<String>) {
    // Get the base name of the resource to use.
    require(args.size in setOf(0, 1)) { "Permitted parameters: answers, answers_nyt, full_list, default=full_list_nyt" }
    val fileArg = args.getOrNull(0) ?: "full_list_nyt"

    // Create the processor.
    val p = Processor.fromCandidates(fileArg)

    // Calculate the data and serialize it.
    val timeInMillis = measureTimeMillis {
        val infoMap = serialize(p, fileArg)

        // Print the word with the highest expected value.
        infoMap.toSortedMap(Comparator { o1, o2 -> when {
            infoMap.lookup(o1) == infoMap.lookup(o2) -> 0
            infoMap.lookup(o1) > infoMap.lookup(o2) -> -1
            else -> 1
        } } ).toList().take(100).forEach { (word, exp) -> println("$word -> $exp") }
    }
    println("Time taken: ${timeInMillis / 1000.0} s.")
}
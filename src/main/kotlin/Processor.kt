// By Sebastian Raaphorst, 2022.

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence


typealias LetterCounts = Map<Char, Int>
typealias LetterRatios = Map<Char, Double>
typealias Frequencies = Map<Int, LetterCounts>
typealias FrequencyRatios = Map<Int, LetterRatios>

// BLACK: Not in.
// GREY: In, but wrong position.
// GREEN: In, and in right position.
enum class Status {
    BLACK,
    GREY,
    GREEN
}

private operator fun Frequencies.plus(other: Frequencies): Frequencies =
        keys.associateWith { k -> this[k]!!.map {
            (ch, ct) -> ch to (ct + ((other[k] ?: emptyMap())[ch] ?: 0))
        }.toMap() }

class Processor constructor(inputFiles: Sequence<Path>, val n: Int, val candidateWords: Set<String>) {
    companion object {
        const val letters = "abcdefghijklmnopqrstuvwxyz"
    }
    // We want a full empty map to make adding easier, as we will have a guarantee of a count for each character.
    // It will also simplify lookups later on as we will have a guaranteed value for each character.
    private val emptyFrequencies: Frequencies =
            (0 until n).associateWith {
                letters.map {
                    it to 0
                }.toMap()
            }

    val frequencies = processFilesToFrequency(inputFiles)
    val ratios = frequencyProcessor(frequencies)

    // For a list of files, process it and find the letter frequency.
    private fun processFilesToFrequency(inputFiles: Sequence<Path>): Frequencies =
            inputFiles.asSequence().fold(emptyFrequencies) { acc, i -> acc + countProcessor(i.toFile().readText(), n) }

    // Take a text, parse it, filter on n letter words, and calculate the frequency of each letter appearing in each
    // position. We only consider the candidate words.
    private fun countProcessor(input: String, n: Int = 5): Frequencies {
        val words = input
                .lowercase()
                .replace('\r', ' ')
                .filter { it.isLetter() || it == ' '}
                .split(' ')
                .asSequence()
                .filterNot(String::isBlank)
                .filter { it.length == n }
                .filter { it in candidateWords }
        return (0 until n).associateWith { p -> words.groupingBy { it[p] }.eachCount() }
    }

    // I don't think we need to do this at all: nothing is gained by normalizing to [0,1] except floating point
    // inaccuracy.
    private fun frequencyProcessor(frequencies: Frequencies): FrequencyRatios {
        // Calculate the number of samples for a position. These are all the same.
        val samples = frequencies[0]?.values?.sum() ?: 0
        return frequencies.map { entry -> entry.key to (entry.value.entries.associate {
            it.key to (it.value.toDouble() / samples) })
        }.toMap()
    }

    fun rankWord(word: String): Int {
        require(word.length == n) { "Word $word has incorrect length n={word.length" }
        return word.withIndex().sumOf { (idx, ch) -> ((frequencies[idx] ?: emptyMap())[ch] ?: 0) }
    }

    fun findCompatibleWords(wordResponse: List<Pair<Char, Status>>): Sequence<String> {
        // The complexity here is to process the black squares correctly.
        // Say we have one or more grey or green letters, e.g. K. in the board.
        // There are two cases:
        // 1. If there are no black Ks in the board, this means we have at least sum of grey and green Ks in board,
        //    but could have more.
        // 2. If there is a black K as well in the board, then we know the exact number of Ks in the board.
        // We determine the minimum amount of time that each letter can appear in the board.
        val minimumTimes = 0
                TODO()
    }
}

fun main() {
    val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
    val resourcesPath = Paths.get(projectDirAbsolutePath, "src/main/resources/data")

    // The list of candidate words for consideration.
    val candidateWords = object {}.javaClass.getResource("/full_list_nyt.txt")!!
            .readText().trim().split("\n").toSet()

    val inputFiles = Files.walk(resourcesPath)
            .asSequence()
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(".txt") }

    println()
    val p = Processor(inputFiles, 5, candidateWords)
    println(p.frequencies)
    println(p.ratios)
    println(p.rankWord("nasal"))

    val candidateMap = candidateWords.map {
        p.rankWord(it) to it
    }.toMap().toSortedMap()
    println(candidateMap)
}
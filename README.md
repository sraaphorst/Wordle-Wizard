# Wordle Wizard

This is just some experimentation to play around with solving Worldle puzzles.
* Python is used to (try to) retrieve the top 100 books from Project Gutenberg as of 2022-03-24.
* Note that this actually ends up being 99 books, as one is not available in txt / UTF-8.
* I then use Kotlin to take in a list of candidate words.
* The candidate words are:
  * `answers.txt`: The answers from the original Wordle game.
  * `answers_nyt.txt`: The answers from the New York Times Wordle game.
  * `full_list.txt`: The full list of candidate words recognized by the original Wordle game.
  * `full_list_nyt.txt`: The full list of candidate words recognized by the New York Times Wordle game.
* The Kotlin code iterates over the downloaded books from Project Gutenberg, parsing off the headers and the footers,
and splitting the text into words. Any words of length not `n` are filtered out, as are any that are not in the
candidate word list.
* We then determine the frequency of each letter in each position from the sample texts by examining them for
occurrences of the candidate words. (This could be made more efficient but is not particularly slow and only needs to
be calculated once.)
* A `word` is scored by adding up the frequencies of the letters in each of the `n` positions.

More forthcoming. This project is early in its development.

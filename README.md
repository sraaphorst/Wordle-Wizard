# Wordle Wizard

This is just some experimentation to play around with solving Wordle puzzles.
* Written in Kotlin, a `Processor` is created with a list of candidate words.
* The candidate word lists available as resources are:
  * `answers.txt`: The answers from the original Wordle game.
  * `answers_nyt.txt`: The answers from the New York Times Wordle game.
  * `full_list.txt`: The full list of candidate words recognized by the original Wordle game (six less than the original).
  * `full_list_nyt.txt`: The full list of candidate words recognized by the New York Times Wordle game (six less than the original).
* We then determine the frequency of each letter in each position from the candidates.
* A `word` is scored by adding up the frequencies of the letters in each of the `n` positions.
* `WordInformation` is used to store cumulative information and be refined across each of the guesses.

More forthcoming. This project is early in its development.

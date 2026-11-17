package com.projectbharat.counterapp

/** Converts number words or numeric tokens found anywhere in a recognized phrase to a count. */
object BabyMovementNumberParser {
    fun extract(phrase: String, english: Boolean): Int {
        val words = phrase.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        val numberMap = if (english) mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
        ) else mapOf(
            "एक" to 1, "एका" to 1, "दोन" to 2, "तीन" to 3, "चार" to 4,
            "पाच" to 5, "पांच" to 5, "सहा" to 6, "सात" to 7, "आठ" to 8,
            "नऊ" to 9, "दहा" to 10, "ek" to 1, "don" to 2, "teen" to 3,
            "tin" to 3, "char" to 4, "chaar" to 4, "pach" to 5, "paach" to 5,
            "saha" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "daha" to 10
        )
        return words.sumOf { token ->
            numberMap[token] ?: token.mapNotNull { ch ->
                Character.digit(ch, 10).takeIf { it >= 0 }
            }.joinToString("").toIntOrNull()?.takeIf { it in 1..10 } ?: 0
        }
    }
}

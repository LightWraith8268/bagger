package com.inknironapps.bagger.ml

object TokenExtractor {
    private val NOISE = setOf("LLC", "INC", "CO", "GMBH", "DISCS", "GOLF", "DISC", "TM", "R")
    private val NUMERIC = Regex("^[0-9]+\\.?[0-9]*$")
    private val SYMBOLS = Regex("[\\u00AE\\u2122\\u00A9]")

    fun extract(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        return rawText
            .replace(SYMBOLS, " ")
            .split(Regex("[\\s\\n\\r\\t,.;:!?()\\[\\]{}/\\\\<>\"']+"))
            .map { it.uppercase().trim() }
            .filter { it.length >= 3 }
            .filter { !NUMERIC.matches(it) }
            .filter { it !in NOISE }
            .distinct()
    }
}

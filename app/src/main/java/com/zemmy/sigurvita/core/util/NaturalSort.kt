package com.zemmy.sigurvita.core.util

/**
 * Natural/human-friendly string comparator that sorts numeric chunks numerically.
 * e.g., "page2" < "page10" (unlike lexicographic sort).
 */
fun naturalCompare(s1: String, s2: String): Int {
    val regex = Regex("""(\d+\.?\d*|\D+)""")
    val chunks1 = regex.findAll(s1).map { it.value }.toList()
    val chunks2 = regex.findAll(s2).map { it.value }.toList()

    val size = minOf(chunks1.size, chunks2.size)
    for (i in 0 until size) {
        val c1 = chunks1[i]
        val c2 = chunks2[i]

        val num1 = c1.toDoubleOrNull()
        val num2 = c2.toDoubleOrNull()

        val cmp = if (num1 != null && num2 != null) {
            num1.compareTo(num2)
        } else {
            c1.lowercase().compareTo(c2.lowercase())
        }

        if (cmp != 0) return cmp
    }

    return chunks1.size.compareTo(chunks2.size)
}

/**
 * Dynamically extracts chapter number from chapter number string and/or title
 * if the chapter number is blank, "0", or "0.0".
 */
fun extractChapterNumber(number: String, title: String): String {
    val trimmedNum = number.trim()
    if (trimmedNum.isNotBlank() && trimmedNum != "0" && trimmedNum != "0.0") {
        return trimmedNum
    }
    val candidateText = title.trim()
    if (candidateText.isBlank()) return trimmedNum.ifBlank { "0" }

    val patterns = listOf(
        Regex("""^(?:Chapter|Issue|Vol\.?)\s*#?(\d+(?:\.\d+)?[A-Za-z]*)""", RegexOption.IGNORE_CASE),
        Regex("""^#?(\d+(?:\.\d+)?[A-Za-z]*)(\s*[-:].*)?"""),
        Regex("""^(\d+(?:\.\d+)?[A-Za-z]*)\b""")
    )

    for (pattern in patterns) {
        val match = pattern.find(candidateText)
        if (match != null) {
            val extracted = match.groupValues.getOrNull(1)
            if (!extracted.isNullOrBlank()) {
                return extracted
            }
        }
    }

    return trimmedNum.ifBlank { "0" }
}


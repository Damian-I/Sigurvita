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

/**
 * Formats a chapter/issue title and chapter number into clean "#X - Title" or "#X" display string.
 */
fun formatChapterDisplayTitle(title: String, chapterNumber: String): String {
    val chNum = extractChapterNumber(chapterNumber, title)
    val isSentinelNumber = chNum.contains("100000") || (chNum.toIntOrNull()?.let { kotlin.math.abs(it) >= 10000 } == true)

    val cleanNumber = if (!isSentinelNumber && chNum.isNotBlank() && chNum != "0") "#$chNum" else ""

    var cleanTitle = title.trim()
    if (chNum.isNotBlank()) {
        cleanTitle = cleanTitle
            .removePrefix("Chapter #$chNum")
            .removePrefix("Chapter $chNum")
            .removePrefix("Issue #$chNum")
            .removePrefix("Issue $chapterNumber")
            .removePrefix("Issue $chNum")
            .removePrefix("#$chNum")
            .removePrefix(chNum)
            .trimStart(' ', '-', ':', '•')
            .trim()
    }

    if (cleanTitle.equals(chNum.trim(), ignoreCase = true) || cleanTitle.equals("Issue $chNum", ignoreCase = true) || cleanTitle.equals("Chapter $chNum", ignoreCase = true)) {
        cleanTitle = ""
    }

    return when {
        cleanNumber.isNotBlank() && cleanTitle.isNotBlank() -> "$cleanNumber - $cleanTitle"
        cleanTitle.isNotBlank() -> cleanTitle
        cleanNumber.isNotBlank() -> cleanNumber
        else -> "Issue"
    }
}

/**
 * Cleans reading list titles by removing bracket prefixes, year ranges, and trailing parenthetical suffixes,
 * resulting in a clean "Era Name - Part Name" string.
 */
fun cleanReadingListTitle(title: String): String {
    var result = title.trim()

    // 1. Strip bracket prefix like [Marvel] or [DC] or [Star Wars]
    result = result.replace(Regex("""^\[[^]]+]\s*"""), "")

    // 2. Strip year range like 2012-2015 or 1961-1970
    result = result.replace(Regex("""\b\d{4}-\d{4}\b\s*"""), "")

    // 3. Repeatedly strip trailing parenthetical suffixes like (2012), (WEB-CBRO), (v1), (MG), etc.
    while (result.contains(Regex("""\s*\([^)]+\)$"""))) {
        result = result.replace(Regex("""\s*\([^)]+\)$"""), "").trim()
    }

    // 4. Strip leading separators like "- " or ": " left after removing year ranges
    result = result.trim().trimStart('-', ':', '•', ' ').trim()

    return result.ifBlank { title }
}



data class EraCategory(
    val name: String,
    val years: String? = null
)

fun extractEraInfo(title: String): EraCategory {
    val cleanTitle = title.trim()

    // Star Wars special rule
    if (cleanTitle.contains("Star Wars", ignoreCase = true)) {
        return EraCategory(name = "Star Wars", years = null)
    }

    // Alternate Universes special rule
    if (cleanTitle.contains("Alternate Universes", ignoreCase = true)) {
        return EraCategory(name = "Alternate Universes", years = null)
    }

    // Year range matching
    val yearRangeRegex = Regex("""(\d{4}-\d{4})""")
    val yearMatch = yearRangeRegex.find(cleanTitle)?.groupValues?.get(1)

    if (yearMatch != null) {
        val name = when (yearMatch) {
            "1939-1956" -> "Golden Age"
            "1961-1970" -> "Silver Age"
            "1970-1984" -> "Bronze Age"
            "1984-1997" -> "Modern Age Begins"
            "1997-2004" -> "Heroes Relaunched"
            "2004-2012" -> "Marvel Disassembled"
            "2012-2015" -> "Marvel NOW!"
            "2015-2018" -> "ANAD Marvel"
            "2018-2021" -> "Fresh Start"
            "2021-2024" -> "Timeless"
            else -> yearMatch
        }
        return EraCategory(name = name, years = yearMatch)
    }

    // Bracket prefix
    val bracketRegex = Regex("""^\[([^]]+)]""")
    val bracketMatch = bracketRegex.find(cleanTitle)?.groupValues?.get(1)
    if (bracketMatch != null && !bracketMatch.equals("Marvel", ignoreCase = true)) {
        return EraCategory(name = bracketMatch, years = null)
    }

    return EraCategory(name = "General", years = null)
}

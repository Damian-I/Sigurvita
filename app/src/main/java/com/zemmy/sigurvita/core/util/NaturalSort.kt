package com.zemmy.sigurvita.core.util

// Pre-compiled regex constants to avoid recompilation on every call
private val CHAPTER_PATTERN_KEYWORD = Regex("""^(?:Chapter|Issue|Vol\.?)\s*#?(\d+(?:\.\d+)?[A-Za-z]*)""", RegexOption.IGNORE_CASE)
private val CHAPTER_PATTERN_HASH = Regex("""^#?(\d+(?:\.\d+)?[A-Za-z]*)(\s*[-:].*)?""")
private val CHAPTER_PATTERN_BARE = Regex("""^(\d+(?:\.\d+)?[A-Za-z]*)\b""")
private val BRACKET_PREFIX_REGEX = Regex("""^\[[^]]+]\s*""")
private val YEAR_RANGE_INLINE_REGEX = Regex("""\b\d{4}-\d{4}\b\s*""")
private val TRAILING_PAREN_REGEX = Regex("""\s*\([^)]+\)$""")
private val YEAR_RANGE_EXTRACT_REGEX = Regex("""(\d{4}-\d{4})""")
private val BRACKET_EXTRACT_REGEX = Regex("""^\[([^]]+)]""")

/**
 * Natural/human-friendly string comparator that sorts numeric chunks numerically
 * without allocating objects, Sequences, or Regex MatchResults.
 * e.g., "page2" < "page10" (unlike lexicographic sort).
 */
fun naturalCompare(s1: String, s2: String): Int {
    var i1 = 0
    var i2 = 0
    val len1 = s1.length
    val len2 = s2.length

    while (i1 < len1 && i2 < len2) {
        val c1 = s1[i1]
        val c2 = s2[i2]

        if (c1.isDigit() && c2.isDigit()) {
            var end1 = i1 + 1
            while (end1 < len1 && s1[end1].isDigit()) end1++

            var end2 = i2 + 1
            while (end2 < len2 && s2[end2].isDigit()) end2++

            val cmp = compareIntegerSegments(s1, i1, end1, s2, i2, end2)
            if (cmp != 0) return cmp

            i1 = end1
            i2 = end2
        } else {
            val u1 = c1.uppercaseChar()
            val u2 = c2.uppercaseChar()
            if (u1 != u2) {
                val l1 = c1.lowercaseChar()
                val l2 = c2.lowercaseChar()
                if (l1 != l2) return l1.compareTo(l2)
            }
            i1++
            i2++
        }
    }

    return (len1 - i1).compareTo(len2 - i2)
}

private fun compareIntegerSegments(
    s1: String, start1: Int, end1: Int,
    s2: String, start2: Int, end2: Int
): Int {
    var p1 = start1
    while (p1 < end1 - 1 && s1[p1] == '0') p1++

    var p2 = start2
    while (p2 < end2 - 1 && s2[p2] == '0') p2++

    val numLen1 = end1 - p1
    val numLen2 = end2 - p2

    if (numLen1 != numLen2) {
        return numLen1.compareTo(numLen2)
    }

    for (k in 0 until numLen1) {
        val diff = s1[p1 + k].compareTo(s2[p2 + k])
        if (diff != 0) return diff
    }

    return (end1 - start1).compareTo(end2 - start2)
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

    val patterns = listOf(CHAPTER_PATTERN_KEYWORD, CHAPTER_PATTERN_HASH, CHAPTER_PATTERN_BARE)

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
fun formatChapterDisplayTitle(title: String, chapterNumber: String, isManga: Boolean = false): String {
    val chNum = extractChapterNumber(chapterNumber, title)
    val isSentinelNumber = chNum.contains("100000") || (chNum.toIntOrNull()?.let { kotlin.math.abs(it) >= 10000 } == true)

    val cleanNumber = if (!isSentinelNumber && chNum.isNotBlank() && chNum != "0") {
        if (isManga) "Chapter #$chNum" else "#$chNum"
    } else ""

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
        else -> if (isManga) "Chapter" else "Issue"
    }
}

/**
 * Cleans reading list titles by removing bracket prefixes, year ranges, and trailing parenthetical suffixes,
 * resulting in a clean "Era Name - Part Name" string.
 */
fun cleanReadingListTitle(title: String): String {
    var result = title.trim()

    // 1. Strip bracket prefix like [Marvel] or [DC] or [Star Wars]
    result = result.replace(BRACKET_PREFIX_REGEX, "")

    // 2. Strip year range like 2012-2015 or 1961-1970
    result = result.replace(YEAR_RANGE_INLINE_REGEX, "")

    // 3. Repeatedly strip trailing parenthetical suffixes like (2012), (WEB-CBRO), (v1), (MG), etc.
    while (result.contains(TRAILING_PAREN_REGEX)) {
        result = result.replace(TRAILING_PAREN_REGEX, "").trim()
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
    val yearMatch = YEAR_RANGE_EXTRACT_REGEX.find(cleanTitle)?.groupValues?.get(1)

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
    val bracketMatch = BRACKET_EXTRACT_REGEX.find(cleanTitle)?.groupValues?.get(1)
    if (bracketMatch != null && !bracketMatch.equals("Marvel", ignoreCase = true)) {
        return EraCategory(name = bracketMatch, years = null)
    }

    return EraCategory(name = "General", years = null)
}

data class ReadingListSubpart(
    val list: com.zemmy.sigurvita.core.model.ReadingList,
    val subpartDisplayTitle: String
)

data class ReadingListPartGroup(
    val partName: String,
    val partDisplayName: String,
    val subparts: List<ReadingListSubpart>
)

data class ReadingListEraGroup(
    val eraName: String,
    val parts: List<ReadingListPartGroup>
)

fun parseReadingListHierarchy(lists: List<com.zemmy.sigurvita.core.model.ReadingList>): List<ReadingListEraGroup> {
    val eraGrouped = lists.groupBy { extractEraInfo(it.title).name }
    return eraGrouped.entries.sortedBy { it.key }.map { (eraName, listsInEra) ->
        val listsWithParsed = listsInEra.map { rl ->
            val (majorPart, subpartDisplay) = parsePartAndSubpart(rl.title)
            Triple(rl, majorPart, subpartDisplay)
        }
        val partGrouped = listsWithParsed.groupBy { it.second }
        val parts = partGrouped.entries.sortedBy { it.key }.map { (majorPart, sublistsInPart) ->
            val sortedSublists = sublistsInPart.sortedWith { t1, t2 ->
                naturalCompare(t1.first.title, t2.first.title)
            }.map { (rl, _, subpartDisplay) ->
                ReadingListSubpart(rl, subpartDisplay)
            }
            val majorNum = majorPart.replace(Regex("""\D+"""), "").toIntOrNull() ?: 1
            ReadingListPartGroup(
                partName = majorPart,
                partDisplayName = "Part $majorNum",
                subparts = sortedSublists
            )
        }
        ReadingListEraGroup(eraName, parts)
    }
}

fun parsePartAndSubpart(title: String): Pair<String, String> {
    val clean = cleanReadingListTitle(title)
    val regex = Regex("""Part\s*(\d+)(?:\.(\d+))?""", RegexOption.IGNORE_CASE)
    val match = regex.find(clean)
    if (match != null) {
        val majorNum = match.groupValues[1].toIntOrNull() ?: 1
        val subNum = match.groupValues[2]

        val majorPartName = "Part %02d".format(majorNum)

        val remainder = clean.replace(regex, "").trimStart('-', ':', '•', ' ').trim()
        val displaySub = if (subNum.isNotBlank()) {
            val subInt = subNum.toIntOrNull() ?: 1
            "$majorNum.$subInt $remainder".trim()
        } else {
            "$majorNum $remainder".trim()
        }
        return Pair(majorPartName, displaySub.ifBlank { clean })
    }
    return Pair("Part 01", clean)
}

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

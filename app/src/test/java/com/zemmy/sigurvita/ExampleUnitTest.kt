package com.zemmy.sigurvita

import com.zemmy.sigurvita.core.util.naturalCompare
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testNaturalCompareNumericOrder() {
        assertTrue(naturalCompare("page2", "page10") < 0)
        assertTrue(naturalCompare("page10", "page2") > 0)
        assertTrue(naturalCompare("Chapter 1", "Chapter 2") < 0)
        assertTrue(naturalCompare("Issue #10", "Issue #2") > 0)
        assertTrue(naturalCompare("1.5", "1.10") < 0)
        assertEquals(0, naturalCompare("Page 1", "page 1"))
    }
}
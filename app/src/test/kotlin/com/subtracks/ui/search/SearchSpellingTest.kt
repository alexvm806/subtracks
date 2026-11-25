package com.subtracks.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSpellingTest {
    @Test
    fun acceptsOneChangeIncludingSwappedLetters() {
        for (variant in listOf("graduation", "graudation", "graduaton", "graduattion", "grxduation")) {
            assertTrue(variant, withinOneEdit("graduation", variant))
        }
        assertFalse(withinOneEdit("graduation", "gradationx"))
    }
}

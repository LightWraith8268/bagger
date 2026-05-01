package com.inknironapps.bagger.ml

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenExtractorTest {
    @Test fun extractsBrandAndMold() {
        val text = "INNOVA\nChampion Destroyer\n175g"
        val tokens = TokenExtractor.extract(text)
        assertTrue("INNOVA" in tokens)
        assertTrue("CHAMPION" in tokens)
        assertTrue("DESTROYER" in tokens)
    }

    @Test fun stripsNoiseWords() {
        val tokens = TokenExtractor.extract("Innova Discs LLC")
        assertTrue("INNOVA" in tokens)
        assertEquals(false, "DISCS" in tokens)
        assertEquals(false, "LLC" in tokens)
    }

    @Test fun stripsTrademarkSymbols() {
        val tokens = TokenExtractor.extract("Aviar® TM ©")
        assertTrue("AVIAR" in tokens)
    }

    @Test fun filtersShortTokens() {
        val tokens = TokenExtractor.extract("X is OK fine")
        assertEquals(false, "X" in tokens)
        assertEquals(false, "IS" in tokens)
        assertEquals(false, "OK" in tokens)
        assertTrue("FINE" in tokens)
    }

    @Test fun filtersNumerics() {
        val tokens = TokenExtractor.extract("Buzzz 175 4 5 -1 1")
        assertTrue("BUZZZ" in tokens)
        assertEquals(false, "175" in tokens)
    }
}

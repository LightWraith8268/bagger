package com.inknironapps.bagger.ml

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JaroWinklerTest {
    @Test fun identicalStrings() {
        assertEquals(1.0, JaroWinkler.similarity("DESTROYER", "DESTROYER"), 0.001)
    }

    @Test fun ocrTypoMatchesHigh() {
        assertTrue(JaroWinkler.similarity("DESTROVER", "DESTROYER") >= 0.90)
    }

    @Test fun unrelatedStringsScoreLow() {
        assertTrue(JaroWinkler.similarity("DESTROYER", "AVIAR") < 0.6)
    }

    @Test fun emptyStringIsZero() {
        assertEquals(0.0, JaroWinkler.similarity("", "AVIAR"), 0.001)
        assertEquals(0.0, JaroWinkler.similarity("AVIAR", ""), 0.001)
    }

    @Test fun prefixMatchBonus() {
        val withPrefix = JaroWinkler.similarity("BUZZZ", "BUZZ")
        val withoutPrefix = JaroWinkler.similarity("XYBUZ", "BUZZ")
        assertTrue(withPrefix > withoutPrefix)
    }
}

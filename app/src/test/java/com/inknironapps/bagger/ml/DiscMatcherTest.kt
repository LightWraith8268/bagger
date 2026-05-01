package com.inknironapps.bagger.ml

import com.inknironapps.bagger.data.db.entity.DiscEntity
import org.junit.Test
import kotlin.test.assertTrue

class DiscMatcherTest {

    private fun disc(id: String, brand: String, mold: String) = DiscEntity(
        id = id, brand = brand, mold = mold,
        speed = 12f, glide = 5f, turn = -1f, fade = 3f,
        discType = "Driver", stability = "overstable",
        pdgaApproved = true, yearReleased = 2008, primaryStampUrl = null
    )

    private val catalog = listOf(
        disc("innova-destroyer", "Innova", "Destroyer"),
        disc("innova-aviar", "Innova", "Aviar"),
        disc("discraft-buzzz", "Discraft", "Buzzz"),
        disc("mvp-tesla", "MVP", "Tesla")
    )

    @Test fun confidentMatchOnExactTokens() {
        val result = DiscMatcher().match(listOf("INNOVA", "DESTROYER"), catalog)
        assertTrue(result is MatchResult.Confident)
        assertTrue((result as MatchResult.Confident).disc.id == "innova-destroyer")
        assertTrue(result.score >= 0.85)
    }

    @Test fun candidatesWhenAmbiguous() {
        val result = DiscMatcher().match(listOf("DEST"), catalog)
        assertTrue(result is MatchResult.Candidates || result is MatchResult.Fallback)
    }

    @Test fun fallbackWhenNoTokens() {
        val result = DiscMatcher().match(emptyList(), catalog)
        assertTrue(result is MatchResult.Fallback)
    }

    @Test fun fallbackWhenAllScoresBelowThreshold() {
        val result = DiscMatcher().match(listOf("ZZZZZ", "QQQQQ"), catalog)
        assertTrue(result is MatchResult.Fallback)
    }
}

package com.inknironapps.bagger.data.changelog

import org.junit.Test
import kotlin.test.assertEquals

class ChangelogParserTest {
    private val sample = """
        # Changelog

        ## [Unreleased]

        ## [0.3.0] - 2026-05-01

        ### Added
        - Sync feature

        ## [0.2.0] - 2026-05-01

        ### Added
        - DB pipeline
        - Schema validation

        ### Changed
        - Test bumped

        ## [0.1.0] - 2026-04-30

        ### Added
        - Initial scaffold
    """.trimIndent()

    @Test
    fun parsesAllVersions() {
        val parsed = ChangelogParser.parse(sample)
        assertEquals(3, parsed.size)
        assertEquals("0.3.0", parsed[0].version)
    }

    @Test
    fun extractsSectionEntries() {
        val parsed = ChangelogParser.parse(sample)
        val v02 = parsed.first { it.version == "0.2.0" }
        assertEquals(2, v02.sections["Added"]?.size)
        assertEquals(1, v02.sections["Changed"]?.size)
    }

    @Test
    fun entriesBetweenIsExclusiveOnFromInclusiveOnTo() {
        val parsed = ChangelogParser.parse(sample)
        val between = ChangelogParser.entriesBetween(parsed, "0.1.0", "0.3.0")
        assertEquals(2, between.size)
        assertEquals(setOf("0.2.0", "0.3.0"), between.map { it.version }.toSet())
    }
}

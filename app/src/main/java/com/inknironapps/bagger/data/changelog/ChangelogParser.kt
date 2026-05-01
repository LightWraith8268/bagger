package com.inknironapps.bagger.data.changelog

object ChangelogParser {

    data class Entry(val version: String, val sections: Map<String, List<String>>)

    fun parse(markdown: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val versionRegex = Regex("^##\\s+\\[?(\\d+\\.\\d+\\.\\d+)\\]?.*$", RegexOption.MULTILINE)
        val matches = versionRegex.findAll(markdown).toList()
        matches.forEachIndexed { idx, m ->
            val version = m.groupValues[1]
            val start = m.range.last + 1
            val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else markdown.length
            val body = markdown.substring(start, end)
            val sections = mutableMapOf<String, MutableList<String>>()
            var currentSection: String? = null
            body.lines().forEach { line ->
                val sectionMatch = Regex("^###\\s+(.+)$").matchEntire(line.trim())
                if (sectionMatch != null) {
                    currentSection = sectionMatch.groupValues[1].trim()
                    sections.getOrPut(currentSection!!) { mutableListOf() }
                } else if (currentSection != null && line.trim().startsWith("- ")) {
                    sections[currentSection]!!.add(line.trim().removePrefix("- "))
                }
            }
            entries.add(Entry(version, sections))
        }
        return entries
    }

    fun entriesBetween(parsed: List<Entry>, from: String?, to: String): List<Entry> {
        val toV = parseVersion(to)
        val fromV = from?.let { parseVersion(it) }
        return parsed.filter { e ->
            val v = parseVersion(e.version)
            compareVersions(v, toV) <= 0 && (fromV == null || compareVersions(v, fromV) > 0)
        }
    }

    private fun parseVersion(s: String): IntArray =
        s.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }.toIntArray()

    private fun compareVersions(a: IntArray, b: IntArray): Int {
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val x = if (i < a.size) a[i] else 0
            val y = if (i < b.size) b[i] else 0
            if (x != y) return x - y
        }
        return 0
    }
}

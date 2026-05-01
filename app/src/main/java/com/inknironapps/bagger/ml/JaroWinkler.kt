package com.inknironapps.bagger.ml

import kotlin.math.max
import kotlin.math.min

object JaroWinkler {
    private const val PREFIX_SCALE = 0.1
    private const val PREFIX_LEN = 4

    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val s1 = a.uppercase()
        val s2 = b.uppercase()
        val matchDistance = max(s1.length, s2.length) / 2 - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)
        var matches = 0
        for (i in s1.indices) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0
        var t = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) t++
            k++
        }
        val transpositions = t / 2.0
        val m = matches.toDouble()
        val jaro = (m / s1.length + m / s2.length + (m - transpositions) / m) / 3.0
        var prefix = 0
        val prefixCap = min(PREFIX_LEN, min(s1.length, s2.length))
        for (i in 0 until prefixCap) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return jaro + prefix * PREFIX_SCALE * (1 - jaro)
    }
}

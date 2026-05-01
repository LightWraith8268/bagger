package com.inknironapps.bagger.ml

import com.inknironapps.bagger.data.db.entity.DiscEntity

class DiscMatcher {

    companion object {
        const val CONFIDENT_THRESHOLD = 0.85
        const val CONFIDENT_GAP = 0.15
        const val CANDIDATE_THRESHOLD = 0.6
        const val MAX_CANDIDATES = 5
        private const val BRAND_WEIGHT = 0.4
        private const val MOLD_WEIGHT = 0.6
    }

    fun match(tokens: List<String>, catalog: List<DiscEntity>): MatchResult {
        if (tokens.isEmpty() || catalog.isEmpty()) return MatchResult.Fallback(tokens)

        val scored = catalog.map { disc -> ScoredDisc(disc, score(tokens, disc)) }
            .sortedByDescending { it.score }

        val top = scored.firstOrNull() ?: return MatchResult.Fallback(tokens)
        val second = scored.getOrNull(1)

        return when {
            top.score >= CONFIDENT_THRESHOLD &&
                (second == null || top.score - second.score >= CONFIDENT_GAP) ->
                MatchResult.Confident(top.disc, top.score)

            top.score >= CANDIDATE_THRESHOLD ->
                MatchResult.Candidates(scored.take(MAX_CANDIDATES).filter { it.score >= CANDIDATE_THRESHOLD })

            else -> MatchResult.Fallback(tokens)
        }
    }

    private fun score(tokens: List<String>, disc: DiscEntity): Double {
        val brandScore = bestMatch(tokens, disc.brand)
        val moldScore = bestMatch(tokens, disc.mold)
        return BRAND_WEIGHT * brandScore + MOLD_WEIGHT * moldScore
    }

    private fun bestMatch(tokens: List<String>, target: String): Double {
        if (target.isEmpty()) return 0.0
        return tokens.maxOfOrNull { JaroWinkler.similarity(it, target) } ?: 0.0
    }
}

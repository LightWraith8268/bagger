package com.inknironapps.bagger.ml

import com.inknironapps.bagger.data.db.entity.DiscEntity

sealed class MatchResult {
    data class Confident(val disc: DiscEntity, val score: Double) : MatchResult()
    data class Candidates(val candidates: List<ScoredDisc>) : MatchResult()
    data class Fallback(val tokens: List<String>) : MatchResult()
}

data class ScoredDisc(val disc: DiscEntity, val score: Double)

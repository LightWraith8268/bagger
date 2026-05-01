package com.inknironapps.bagger.domain.model

enum class DiscCondition {
    New, Good, Beat, Dyed;

    companion object {
        fun fromString(s: String): DiscCondition =
            entries.firstOrNull { it.name == s } ?: Good
    }
}

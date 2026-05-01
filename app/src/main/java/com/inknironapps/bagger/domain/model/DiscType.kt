package com.inknironapps.bagger.domain.model

enum class DiscType {
    Putter, Approach, Mid, Fairway, Driver;

    companion object {
        fun fromString(s: String): DiscType =
            entries.firstOrNull { it.name == s } ?: Driver
    }
}

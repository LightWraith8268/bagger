package com.inknironapps.bagger.domain.model

enum class DiscState {
    Shelf, InBag, Lost, Found, Sold, Traded, Retired, Gifted;

    companion object {
        fun fromString(s: String): DiscState =
            entries.firstOrNull { it.name == s } ?: Shelf
    }
}

package com.inknironapps.bagger.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.takeIf { it.isNotEmpty() }?.split(",") ?: emptyList()
}

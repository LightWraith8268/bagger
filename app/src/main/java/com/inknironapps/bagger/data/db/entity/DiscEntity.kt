package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discs")
data class DiscEntity(
    @PrimaryKey val id: String,
    val brand: String,
    val mold: String,
    val speed: Float,
    val glide: Float,
    val turn: Float,
    val fade: Float,
    val discType: String,
    val stability: String,
    val pdgaApproved: Boolean,
    val yearReleased: Int?,
    val primaryStampUrl: String?,
    val maxWeightG: Float? = null,
    val diameterCm: Float? = null,
    val discClass: String? = null
)

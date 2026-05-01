package com.inknironapps.bagger.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class DiscDto(
    val id: String,
    val brand: String,
    val mold: String,
    val speed: Float,
    val glide: Float,
    val turn: Float,
    val fade: Float,
    val discType: String,
    val stability: String,
    val pdgaApproved: Boolean,
    val yearReleased: Int? = null,
    val primaryStampUrl: String? = null,
    val aliases: List<String> = emptyList(),
    val schemaVersion: Int = 1
)

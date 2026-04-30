package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bags")
data class BagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val iconColor: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String?,
    val syncedAt: Long?
)

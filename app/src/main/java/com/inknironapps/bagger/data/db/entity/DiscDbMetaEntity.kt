package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disc_db_meta")
data class DiscDbMetaEntity(
    @PrimaryKey val id: Int = 1,
    val lastSyncedAt: Long,
    val etag: String?,
    val discCount: Int,
    val schemaVersion: Int
)

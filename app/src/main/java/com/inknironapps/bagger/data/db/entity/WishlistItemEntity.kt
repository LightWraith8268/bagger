package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wishlist_items",
    foreignKeys = [ForeignKey(
        entity = DiscEntity::class, parentColumns = ["id"], childColumns = ["discId"]
    )],
    indices = [Index("discId")]
)
data class WishlistItemEntity(
    @PrimaryKey val id: String,
    val discId: String,
    val addedAt: Long,
    val targetWeight: Int?,
    val targetPlastic: String?,
    val notes: String?
)

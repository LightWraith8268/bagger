package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "owned_discs",
    foreignKeys = [
        ForeignKey(entity = DiscEntity::class, parentColumns = ["id"], childColumns = ["discId"]),
        ForeignKey(entity = BagEntity::class, parentColumns = ["id"], childColumns = ["bagId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("discId"), Index("state", "bagId")]
)
data class OwnedDiscEntity(
    @PrimaryKey val id: String,
    val discId: String,
    val plasticType: String?,
    val weight: Int?,
    val color: String?,
    val condition: String,
    val state: String,
    val bagId: String?,
    val purchaseDate: Long?,
    val purchasePrice: Long?,
    val notes: String?,
    val isOriginalOwner: Boolean,
    val customTags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String?,
    val syncedAt: Long?
)

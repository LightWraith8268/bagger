package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "owned_disc_photos",
    foreignKeys = [ForeignKey(
        entity = OwnedDiscEntity::class, parentColumns = ["id"], childColumns = ["ownedDiscId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("ownedDiscId")]
)
data class OwnedDiscPhotoEntity(
    @PrimaryKey val id: String,
    val ownedDiscId: String,
    val localPath: String,
    val type: String,
    val capturedAt: Long
)

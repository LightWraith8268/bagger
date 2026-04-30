package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lost_disc_events",
    foreignKeys = [ForeignKey(
        entity = OwnedDiscEntity::class, parentColumns = ["id"], childColumns = ["ownedDiscId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("ownedDiscId", "foundAt")]
)
data class LostDiscEventEntity(
    @PrimaryKey val id: String,
    val ownedDiscId: String,
    val lostAt: Long,
    val lat: Double?,
    val lng: Double?,
    val courseName: String?,
    val holeNumber: Int?,
    val notes: String?,
    val foundAt: Long?
)

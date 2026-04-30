package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "id_submission_queue")
data class IdSubmissionQueueEntity(
    @PrimaryKey val id: String,
    val photoPath: String,
    val confirmedDiscId: String,
    val ocrTokens: List<String>,
    val capturedAt: Long
)

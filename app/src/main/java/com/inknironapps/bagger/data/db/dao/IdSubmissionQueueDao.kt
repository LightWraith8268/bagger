package com.inknironapps.bagger.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.inknironapps.bagger.data.db.entity.IdSubmissionQueueEntity

@Dao
interface IdSubmissionQueueDao {
    @Upsert suspend fun upsert(entry: IdSubmissionQueueEntity)
    @Query("SELECT * FROM id_submission_queue ORDER BY capturedAt DESC") suspend fun getAll(): List<IdSubmissionQueueEntity>
    @Delete suspend fun delete(entry: IdSubmissionQueueEntity)
}

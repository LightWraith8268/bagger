package com.inknironapps.bagger.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LostDiscEventDao {
    @Upsert suspend fun upsert(event: LostDiscEventEntity)

    @Query("SELECT * FROM lost_disc_events WHERE foundAt IS NULL ORDER BY lostAt DESC")
    fun observeUnfound(): Flow<List<LostDiscEventEntity>>

    @Query("SELECT * FROM lost_disc_events WHERE ownedDiscId = :id ORDER BY lostAt DESC")
    fun observeForDisc(id: String): Flow<List<LostDiscEventEntity>>
}

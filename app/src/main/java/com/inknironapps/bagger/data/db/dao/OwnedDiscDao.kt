package com.inknironapps.bagger.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedDiscDao {
    @Upsert suspend fun upsert(disc: OwnedDiscEntity)

    @Delete suspend fun delete(disc: OwnedDiscEntity)

    @Query("SELECT * FROM owned_discs ORDER BY updatedAt DESC")
    suspend fun getAll(): List<OwnedDiscEntity>

    @Query("SELECT * FROM owned_discs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<OwnedDiscEntity>>

    @Query("SELECT * FROM owned_discs WHERE id = :id")
    fun observeById(id: String): Flow<OwnedDiscEntity?>

    @Query("SELECT * FROM owned_discs WHERE state = :state")
    fun observeByState(state: String): Flow<List<OwnedDiscEntity>>

    @Query("SELECT * FROM owned_discs WHERE bagId = :bagId")
    fun observeByBag(bagId: String): Flow<List<OwnedDiscEntity>>
}

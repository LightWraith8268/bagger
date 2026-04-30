package com.inknironapps.bagger.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.inknironapps.bagger.data.db.entity.BagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BagDao {
    @Upsert suspend fun upsert(bag: BagEntity)

    @Delete suspend fun delete(bag: BagEntity)

    @Query("SELECT * FROM bags ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<BagEntity>>

    @Query("SELECT * FROM bags WHERE id = :id")
    suspend fun getById(id: String): BagEntity?
}

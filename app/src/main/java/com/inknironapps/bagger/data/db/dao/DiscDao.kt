package com.inknironapps.bagger.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.inknironapps.bagger.data.db.entity.DiscEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscDao {
    @Upsert suspend fun upsertAll(discs: List<DiscEntity>)

    @Query("SELECT * FROM discs WHERE id = :id")
    suspend fun getById(id: String): DiscEntity?

    @Query("SELECT * FROM discs ORDER BY brand, mold")
    fun observeAll(): Flow<List<DiscEntity>>

    @Query("SELECT COUNT(*) FROM discs")
    suspend fun count(): Int

    @Query("SELECT * FROM discs WHERE LOWER(brand) LIKE '%' || LOWER(:q) || '%' OR LOWER(mold) LIKE '%' || LOWER(:q) || '%'")
    fun search(q: String): Flow<List<DiscEntity>>
}

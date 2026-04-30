package com.inknironapps.bagger.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.inknironapps.bagger.data.db.entity.DiscDbMetaEntity

@Dao
interface DiscDbMetaDao {
    @Upsert suspend fun upsert(meta: DiscDbMetaEntity)

    @Query("SELECT * FROM disc_db_meta WHERE id = 1")
    suspend fun get(): DiscDbMetaEntity?
}

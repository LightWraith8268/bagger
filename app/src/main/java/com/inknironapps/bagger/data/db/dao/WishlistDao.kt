package com.inknironapps.bagger.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Upsert suspend fun upsert(item: WishlistItemEntity)

    @Delete suspend fun delete(item: WishlistItemEntity)

    @Query("SELECT * FROM wishlist_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WishlistItemEntity>>
}

package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun observeAll(): Flow<List<WishlistItemEntity>>
    suspend fun upsert(item: WishlistItemEntity)
    suspend fun delete(item: WishlistItemEntity)
}

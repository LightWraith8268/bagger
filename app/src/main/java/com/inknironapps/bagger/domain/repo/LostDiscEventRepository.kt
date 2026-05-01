package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import kotlinx.coroutines.flow.Flow

interface LostDiscEventRepository {
    fun observeUnfound(): Flow<List<LostDiscEventEntity>>
    fun observeForDisc(discId: String): Flow<List<LostDiscEventEntity>>
    suspend fun upsert(event: LostDiscEventEntity)
}

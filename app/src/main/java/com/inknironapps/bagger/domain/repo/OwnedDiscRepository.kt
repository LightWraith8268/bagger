package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import kotlinx.coroutines.flow.Flow

interface OwnedDiscRepository {
    fun observeAll(): Flow<List<OwnedDiscEntity>>
    fun observeById(id: String): Flow<OwnedDiscEntity?>
    fun observeByState(state: String): Flow<List<OwnedDiscEntity>>
    fun observeByBag(bagId: String): Flow<List<OwnedDiscEntity>>
    suspend fun upsert(disc: OwnedDiscEntity)
    suspend fun delete(disc: OwnedDiscEntity)
}

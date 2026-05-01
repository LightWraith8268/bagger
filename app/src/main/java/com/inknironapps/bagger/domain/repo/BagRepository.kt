package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.BagEntity
import kotlinx.coroutines.flow.Flow

interface BagRepository {
    fun observeAll(): Flow<List<BagEntity>>
    suspend fun getById(id: String): BagEntity?
    suspend fun upsert(bag: BagEntity)
    suspend fun delete(bag: BagEntity)
}

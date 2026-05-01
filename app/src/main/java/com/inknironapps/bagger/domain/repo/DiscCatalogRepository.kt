package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.DiscEntity
import kotlinx.coroutines.flow.Flow

interface DiscCatalogRepository {
    fun observeAll(): Flow<List<DiscEntity>>
    fun search(query: String): Flow<List<DiscEntity>>
    suspend fun getById(id: String): DiscEntity?
}

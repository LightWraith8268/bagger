package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.OwnedDiscPhotoEntity
import kotlinx.coroutines.flow.Flow

interface OwnedDiscPhotoRepository {
    fun observeForDisc(discId: String): Flow<List<OwnedDiscPhotoEntity>>
    suspend fun upsert(photo: OwnedDiscPhotoEntity)
    suspend fun delete(photo: OwnedDiscPhotoEntity)
}

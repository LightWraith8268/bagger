package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.OwnedDiscPhotoDao
import com.inknironapps.bagger.data.db.entity.OwnedDiscPhotoEntity
import com.inknironapps.bagger.domain.repo.OwnedDiscPhotoRepository
import javax.inject.Inject

class OwnedDiscPhotoRepositoryImpl @Inject constructor(
    private val dao: OwnedDiscPhotoDao
) : OwnedDiscPhotoRepository {
    override fun observeForDisc(discId: String) = dao.observeForDisc(discId)
    override suspend fun upsert(photo: OwnedDiscPhotoEntity) = dao.upsert(photo)
    override suspend fun delete(photo: OwnedDiscPhotoEntity) = dao.delete(photo)
}

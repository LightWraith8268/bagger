package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import javax.inject.Inject

class OwnedDiscRepositoryImpl @Inject constructor(
    private val dao: OwnedDiscDao
) : OwnedDiscRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeById(id: String) = dao.observeById(id)
    override fun observeByState(state: String) = dao.observeByState(state)
    override fun observeByBag(bagId: String) = dao.observeByBag(bagId)
    override suspend fun upsert(disc: OwnedDiscEntity) = dao.upsert(disc)
    override suspend fun delete(disc: OwnedDiscEntity) = dao.delete(disc)
}

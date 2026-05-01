package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.BagDao
import com.inknironapps.bagger.data.db.entity.BagEntity
import com.inknironapps.bagger.domain.repo.BagRepository
import javax.inject.Inject

class BagRepositoryImpl @Inject constructor(
    private val dao: BagDao
) : BagRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: String) = dao.getById(id)
    override suspend fun upsert(bag: BagEntity) = dao.upsert(bag)
    override suspend fun delete(bag: BagEntity) = dao.delete(bag)
}

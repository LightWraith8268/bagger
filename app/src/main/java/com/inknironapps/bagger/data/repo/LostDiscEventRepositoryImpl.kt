package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.LostDiscEventDao
import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import com.inknironapps.bagger.domain.repo.LostDiscEventRepository
import javax.inject.Inject

class LostDiscEventRepositoryImpl @Inject constructor(
    private val dao: LostDiscEventDao
) : LostDiscEventRepository {
    override fun observeUnfound() = dao.observeUnfound()
    override fun observeForDisc(discId: String) = dao.observeForDisc(discId)
    override suspend fun upsert(event: LostDiscEventEntity) = dao.upsert(event)
}

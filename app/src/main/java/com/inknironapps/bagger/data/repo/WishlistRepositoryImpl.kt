package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.WishlistDao
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import com.inknironapps.bagger.domain.repo.WishlistRepository
import javax.inject.Inject

class WishlistRepositoryImpl @Inject constructor(
    private val dao: WishlistDao
) : WishlistRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun upsert(item: WishlistItemEntity) = dao.upsert(item)
    override suspend fun delete(item: WishlistItemEntity) = dao.delete(item)
}

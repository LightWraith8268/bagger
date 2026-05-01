package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import javax.inject.Inject

class DiscCatalogRepositoryImpl @Inject constructor(
    private val dao: DiscDao
) : DiscCatalogRepository {
    override fun observeAll() = dao.observeAll()
    override fun search(query: String) = dao.search(query)
    override suspend fun getById(id: String) = dao.getById(id)
}

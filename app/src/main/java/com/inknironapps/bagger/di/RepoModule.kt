package com.inknironapps.bagger.di

import com.inknironapps.bagger.data.repo.BagRepositoryImpl
import com.inknironapps.bagger.data.repo.DiscCatalogRepositoryImpl
import com.inknironapps.bagger.data.repo.OwnedDiscRepositoryImpl
import com.inknironapps.bagger.domain.repo.BagRepository
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {
    @Binds @Singleton abstract fun bindCatalog(impl: DiscCatalogRepositoryImpl): DiscCatalogRepository
    @Binds @Singleton abstract fun bindOwned(impl: OwnedDiscRepositoryImpl): OwnedDiscRepository
    @Binds @Singleton abstract fun bindBag(impl: BagRepositoryImpl): BagRepository
}

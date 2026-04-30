package com.inknironapps.bagger.di

import android.content.Context
import androidx.room.Room
import com.inknironapps.bagger.data.db.BaggerDatabase
import com.inknironapps.bagger.data.db.dao.BagDao
import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.data.db.dao.DiscDbMetaDao
import com.inknironapps.bagger.data.db.dao.LostDiscEventDao
import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import com.inknironapps.bagger.data.db.dao.WishlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BaggerDatabase =
        Room.databaseBuilder(context, BaggerDatabase::class.java, "bagger.db")
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()

    @Provides fun provideDiscDao(db: BaggerDatabase): DiscDao = db.discDao()
    @Provides fun provideOwnedDiscDao(db: BaggerDatabase): OwnedDiscDao = db.ownedDiscDao()
    @Provides fun provideBagDao(db: BaggerDatabase): BagDao = db.bagDao()
    @Provides fun provideLostDiscEventDao(db: BaggerDatabase): LostDiscEventDao = db.lostDiscEventDao()
    @Provides fun provideWishlistDao(db: BaggerDatabase): WishlistDao = db.wishlistDao()
    @Provides fun provideDiscDbMetaDao(db: BaggerDatabase): DiscDbMetaDao = db.discDbMetaDao()
}

package com.inknironapps.bagger.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inknironapps.bagger.data.db.dao.BagDao
import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.data.db.dao.DiscDbMetaDao
import com.inknironapps.bagger.data.db.dao.IdSubmissionQueueDao
import com.inknironapps.bagger.data.db.dao.LostDiscEventDao
import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import com.inknironapps.bagger.data.db.dao.WishlistDao
import com.inknironapps.bagger.data.db.entity.BagEntity
import com.inknironapps.bagger.data.db.entity.DiscDbMetaEntity
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.IdSubmissionQueueEntity
import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscPhotoEntity
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity

@Database(
    entities = [
        DiscEntity::class,
        OwnedDiscEntity::class,
        OwnedDiscPhotoEntity::class,
        BagEntity::class,
        LostDiscEventEntity::class,
        WishlistItemEntity::class,
        DiscDbMetaEntity::class,
        IdSubmissionQueueEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BaggerDatabase : RoomDatabase() {
    abstract fun discDao(): DiscDao
    abstract fun ownedDiscDao(): OwnedDiscDao
    abstract fun bagDao(): BagDao
    abstract fun lostDiscEventDao(): LostDiscEventDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun discDbMetaDao(): DiscDbMetaDao
    abstract fun idSubmissionQueueDao(): IdSubmissionQueueDao
}

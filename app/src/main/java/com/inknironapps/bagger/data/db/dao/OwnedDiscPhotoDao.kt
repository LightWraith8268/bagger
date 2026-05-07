package com.inknironapps.bagger.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.inknironapps.bagger.data.db.entity.OwnedDiscPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedDiscPhotoDao {
    @Upsert suspend fun upsert(photo: OwnedDiscPhotoEntity)

    @Delete suspend fun delete(photo: OwnedDiscPhotoEntity)

    @Query("SELECT * FROM owned_disc_photos WHERE ownedDiscId = :discId ORDER BY capturedAt DESC")
    fun observeForDisc(discId: String): Flow<List<OwnedDiscPhotoEntity>>
}

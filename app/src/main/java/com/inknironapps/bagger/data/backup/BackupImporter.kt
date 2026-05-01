package com.inknironapps.bagger.data.backup

import com.inknironapps.bagger.data.db.dao.BagDao
import com.inknironapps.bagger.data.db.dao.LostDiscEventDao
import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import com.inknironapps.bagger.data.db.dao.WishlistDao
import com.inknironapps.bagger.data.db.entity.BagEntity
import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporter @Inject constructor(
    private val ownedDao: OwnedDiscDao,
    private val bagDao: BagDao,
    private val lostDao: LostDiscEventDao,
    private val wishlistDao: WishlistDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed class Result {
        data class Success(val counts: Counts) : Result()
        data class Failure(val message: String) : Result()
    }

    data class Counts(val owned: Int, val bags: Int, val lost: Int, val wishlist: Int)

    suspend fun import(jsonText: String): Result = runCatching {
        val backup = json.decodeFromString(BaggerBackup.serializer(), jsonText)
        if (backup.schemaVersion > 1) {
            return@runCatching Result.Failure("Backup is from a newer app version. Please update Bagger.")
        }

        backup.bags.forEach { b ->
            bagDao.upsert(
                BagEntity(
                    b.id, b.name, b.description, b.iconColor, b.sortOrder,
                    b.createdAt, b.updatedAt, null, null
                )
            )
        }
        backup.ownedDiscs.forEach { o ->
            ownedDao.upsert(
                OwnedDiscEntity(
                    o.id, o.discId, o.plasticType, o.weight, o.color, o.condition, o.state,
                    o.bagId, o.purchaseDate, o.purchasePrice, o.notes, o.isOriginalOwner,
                    o.customTags, o.createdAt, o.updatedAt, null, null
                )
            )
        }
        backup.lostEvents.forEach { l ->
            lostDao.upsert(
                LostDiscEventEntity(
                    l.id, l.ownedDiscId, l.lostAt, l.lat, l.lng,
                    l.courseName, l.holeNumber, l.notes, l.foundAt
                )
            )
        }
        backup.wishlist.forEach { w ->
            wishlistDao.upsert(
                WishlistItemEntity(
                    w.id, w.discId, w.addedAt, w.targetWeight, w.targetPlastic, w.notes
                )
            )
        }
        Result.Success(
            Counts(
                backup.ownedDiscs.size,
                backup.bags.size,
                backup.lostEvents.size,
                backup.wishlist.size
            )
        )
    }.getOrElse { Result.Failure(it.message ?: "Import failed") }
}

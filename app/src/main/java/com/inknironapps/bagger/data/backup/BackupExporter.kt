package com.inknironapps.bagger.data.backup

import com.inknironapps.bagger.data.db.dao.BagDao
import com.inknironapps.bagger.data.db.dao.LostDiscEventDao
import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import com.inknironapps.bagger.data.db.dao.WishlistDao
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BaggerBackup(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val ownedDiscs: List<OwnedDiscBackup>,
    val bags: List<BagBackup>,
    val lostEvents: List<LostBackup>,
    val wishlist: List<WishlistBackup>
)

@Serializable
data class OwnedDiscBackup(
    val id: String,
    val discId: String,
    val plasticType: String?,
    val weight: Int?,
    val color: String?,
    val condition: String,
    val state: String,
    val bagId: String?,
    val purchaseDate: Long?,
    val purchasePrice: Long?,
    val notes: String?,
    val isOriginalOwner: Boolean,
    val customTags: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BagBackup(
    val id: String,
    val name: String,
    val description: String?,
    val iconColor: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class LostBackup(
    val id: String,
    val ownedDiscId: String,
    val lostAt: Long,
    val lat: Double?,
    val lng: Double?,
    val courseName: String?,
    val holeNumber: Int?,
    val notes: String?,
    val foundAt: Long?
)

@Serializable
data class WishlistBackup(
    val id: String,
    val discId: String,
    val addedAt: Long,
    val targetWeight: Int?,
    val targetPlastic: String?,
    val notes: String?
)

@Singleton
class BackupExporter @Inject constructor(
    private val ownedDao: OwnedDiscDao,
    private val bagDao: BagDao,
    private val lostDao: LostDiscEventDao,
    private val wishlistDao: WishlistDao
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun export(): String {
        val owned = ownedDao.getAll().map {
            OwnedDiscBackup(
                it.id, it.discId, it.plasticType, it.weight, it.color, it.condition, it.state,
                it.bagId, it.purchaseDate, it.purchasePrice, it.notes, it.isOriginalOwner,
                it.customTags, it.createdAt, it.updatedAt
            )
        }
        val bags = bagDao.getAllOnce()
        val lost = lostDao.getAllOnce()
        val wishlist = wishlistDao.getAllOnce()
        val backup = BaggerBackup(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            ownedDiscs = owned,
            bags = bags.map {
                BagBackup(
                    it.id, it.name, it.description, it.iconColor, it.sortOrder,
                    it.createdAt, it.updatedAt
                )
            },
            lostEvents = lost.map {
                LostBackup(
                    it.id, it.ownedDiscId, it.lostAt, it.lat, it.lng,
                    it.courseName, it.holeNumber, it.notes, it.foundAt
                )
            },
            wishlist = wishlist.map {
                WishlistBackup(
                    it.id, it.discId, it.addedAt, it.targetWeight, it.targetPlastic, it.notes
                )
            }
        )
        return json.encodeToString(BaggerBackup.serializer(), backup)
    }
}

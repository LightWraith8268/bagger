package com.inknironapps.bagger.ui.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import com.inknironapps.bagger.domain.repo.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WishlistUi(
    val items: List<WishlistItemEntity> = emptyList(),
    val catalog: Map<String, DiscEntity> = emptyMap()
)

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val wishlistRepo: WishlistRepository,
    private val catalogRepo: DiscCatalogRepository,
    private val ownedRepo: OwnedDiscRepository
) : ViewModel() {

    val ui: StateFlow<WishlistUi> = combine(
        wishlistRepo.observeAll(),
        catalogRepo.observeAll()
    ) { items, catalog -> WishlistUi(items, catalog.associateBy { it.id }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishlistUi())

    fun add(disc: DiscEntity) {
        viewModelScope.launch {
            wishlistRepo.upsert(
                WishlistItemEntity(
                    id = UUID.randomUUID().toString(),
                    discId = disc.id,
                    addedAt = System.currentTimeMillis(),
                    targetWeight = null,
                    targetPlastic = null,
                    notes = null
                )
            )
        }
    }

    fun remove(item: WishlistItemEntity) {
        viewModelScope.launch { wishlistRepo.delete(item) }
    }

    fun convertToOwned(item: WishlistItemEntity) {
        val disc = ui.value.catalog[item.discId] ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            ownedRepo.upsert(
                OwnedDiscEntity(
                    id = UUID.randomUUID().toString(),
                    discId = disc.id,
                    plasticType = item.targetPlastic,
                    weight = item.targetWeight,
                    color = null,
                    condition = "New",
                    state = "Shelf",
                    bagId = null,
                    purchaseDate = now,
                    purchasePrice = null,
                    notes = item.notes,
                    isOriginalOwner = true,
                    customTags = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                    userId = null,
                    syncedAt = null
                )
            )
            wishlistRepo.delete(item)
        }
    }
}

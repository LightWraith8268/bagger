package com.inknironapps.bagger.ui.screens.disc_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import com.inknironapps.bagger.domain.repo.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CatalogDetailUi(
    val disc: DiscEntity? = null,
    val added: Boolean = false,
    val wishlisted: Boolean = false
)

@HiltViewModel
class CatalogDiscDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalogRepo: DiscCatalogRepository,
    private val ownedRepo: OwnedDiscRepository,
    private val wishlistRepo: WishlistRepository
) : ViewModel() {
    private val discId: String = checkNotNull(savedState["discId"])
    private val _ui = MutableStateFlow(CatalogDetailUi())
    val ui: StateFlow<CatalogDetailUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { _ui.value = CatalogDetailUi(catalogRepo.getById(discId)) }
    }

    fun addToShelf() {
        val disc = _ui.value.disc ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            ownedRepo.upsert(
                OwnedDiscEntity(
                    id = UUID.randomUUID().toString(),
                    discId = disc.id,
                    plasticType = null,
                    weight = null,
                    color = null,
                    condition = "Good",
                    state = "Shelf",
                    bagId = null,
                    purchaseDate = null,
                    purchasePrice = null,
                    notes = null,
                    isOriginalOwner = true,
                    customTags = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                    userId = null,
                    syncedAt = null
                )
            )
            _ui.value = _ui.value.copy(added = true)
        }
    }

    fun addToWishlist() {
        val disc = _ui.value.disc ?: return
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
            _ui.value = _ui.value.copy(wishlisted = true)
        }
    }
}

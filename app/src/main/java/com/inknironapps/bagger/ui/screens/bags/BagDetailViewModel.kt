package com.inknironapps.bagger.ui.screens.bags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.BagEntity
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.domain.repo.BagRepository
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BagDetailUiState(
    val bag: BagEntity? = null,
    val discs: List<OwnedDiscEntity> = emptyList(),
    val catalog: Map<String, DiscEntity> = emptyMap()
)

@HiltViewModel
class BagDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val bagRepo: BagRepository,
    private val ownedRepo: OwnedDiscRepository,
    private val catalogRepo: DiscCatalogRepository
) : ViewModel() {
    private val bagId: String = checkNotNull(savedState["bagId"])

    val ui: StateFlow<BagDetailUiState> = combine(
        ownedRepo.observeByBag(bagId),
        catalogRepo.observeAll(),
        flow { emit(bagRepo.getById(bagId)) }
    ) { discs, catalog, bag ->
        BagDetailUiState(bag, discs, catalog.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BagDetailUiState())

    fun removeDiscFromBag(disc: OwnedDiscEntity) {
        viewModelScope.launch {
            ownedRepo.upsert(
                disc.copy(
                    bagId = null,
                    state = "Shelf",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

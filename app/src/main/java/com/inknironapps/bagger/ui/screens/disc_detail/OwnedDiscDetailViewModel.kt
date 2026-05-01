package com.inknironapps.bagger.ui.screens.disc_detail

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnedDetailUi(
    val owned: OwnedDiscEntity? = null,
    val catalog: DiscEntity? = null,
    val bags: List<BagEntity> = emptyList()
)

@HiltViewModel
class OwnedDiscDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ownedRepo: OwnedDiscRepository,
    private val catalogRepo: DiscCatalogRepository,
    bagRepo: BagRepository
) : ViewModel() {
    private val ownedId: String = checkNotNull(savedState["ownedId"])

    val ui: StateFlow<OwnedDetailUi> = combine(
        ownedRepo.observeById(ownedId),
        bagRepo.observeAll()
    ) { owned, bags -> Pair(owned, bags) }
        .map { (owned, bags) ->
            val catalog = owned?.let { catalogRepo.getById(it.discId) }
            OwnedDetailUi(owned, catalog, bags)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OwnedDetailUi())

    fun changeState(state: String, bagId: String? = null) {
        val o = ui.value.owned ?: return
        viewModelScope.launch {
            ownedRepo.upsert(
                o.copy(
                    state = state,
                    bagId = bagId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun delete() {
        val o = ui.value.owned ?: return
        viewModelScope.launch { ownedRepo.delete(o) }
    }
}

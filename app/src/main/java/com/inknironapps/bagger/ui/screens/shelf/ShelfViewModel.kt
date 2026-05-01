package com.inknironapps.bagger.ui.screens.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ShelfUiState(
    val owned: List<OwnedDiscEntity> = emptyList(),
    val catalog: Map<String, DiscEntity> = emptyMap(),
    val filterState: String? = null,
    val filterBrand: String? = null
)

@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val ownedRepo: OwnedDiscRepository,
    private val catalogRepo: DiscCatalogRepository
) : ViewModel() {

    private val filter = MutableStateFlow<Pair<String?, String?>>(null to null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<ShelfUiState> = combine(
        filter.flatMapLatest { (s, _) ->
            if (s == null) ownedRepo.observeAll() else ownedRepo.observeByState(s)
        },
        catalogRepo.observeAll(),
        filter
    ) { owned, catalog, (s, b) ->
        val map = catalog.associateBy { it.id }
        val filtered = if (b == null) owned else owned.filter { (map[it.discId]?.brand) == b }
        ShelfUiState(filtered, map, s, b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShelfUiState())

    fun setStateFilter(s: String?) { filter.value = s to filter.value.second }
    fun setBrandFilter(b: String?) { filter.value = filter.value.first to b }
}

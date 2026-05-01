package com.inknironapps.bagger.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DiscoverUiState(
    val query: String = "",
    val typeFilter: String? = null,
    val results: List<DiscEntity> = emptyList()
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repo: DiscCatalogRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<DiscoverUiState> = combine(query, typeFilter) { q, t -> q to t }
        .flatMapLatest { (q, t) ->
            val flow = if (q.isBlank()) repo.observeAll() else repo.search(q)
            flow.map { discs ->
                val filtered = if (t == null) discs else discs.filter { it.discType == t }
                DiscoverUiState(q, t, filtered)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscoverUiState())

    fun setQuery(q: String) { query.value = q }
    fun setType(t: String?) { typeFilter.value = t }
}

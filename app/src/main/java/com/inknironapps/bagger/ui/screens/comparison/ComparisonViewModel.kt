package com.inknironapps.bagger.ui.screens.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CompareUi(
    val all: List<DiscEntity> = emptyList(),
    val picked: List<DiscEntity> = emptyList(),
    val query: String = ""
)

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    catalogRepo: DiscCatalogRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val pickedIds = MutableStateFlow<List<String>>(emptyList())

    val ui: StateFlow<CompareUi> = combine(
        catalogRepo.observeAll(),
        query,
        pickedIds
    ) { all, q, ids ->
        val byId = all.associateBy { it.id }
        val results = if (q.isBlank()) all else all.filter {
            it.brand.contains(q, true) || it.mold.contains(q, true)
        }
        CompareUi(
            all = results,
            picked = ids.mapNotNull { byId[it] },
            query = q
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompareUi())

    fun setQuery(q: String) { query.value = q }

    fun togglePick(disc: DiscEntity) {
        val cur = pickedIds.value.toMutableList()
        if (cur.contains(disc.id)) cur.remove(disc.id)
        else if (cur.size < 3) cur.add(disc.id)
        pickedIds.value = cur
    }
}

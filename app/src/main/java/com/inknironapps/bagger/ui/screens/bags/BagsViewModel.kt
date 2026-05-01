package com.inknironapps.bagger.ui.screens.bags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.BagEntity
import com.inknironapps.bagger.domain.repo.BagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BagsUiState(val bags: List<BagEntity> = emptyList())

@HiltViewModel
class BagsViewModel @Inject constructor(
    private val repo: BagRepository
) : ViewModel() {
    val ui: StateFlow<BagsUiState> = repo.observeAll()
        .map { BagsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BagsUiState())

    fun createBag(name: String) {
        if (name.isBlank()) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repo.upsert(
                BagEntity(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    description = null,
                    iconColor = "#095F73",
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                    userId = null,
                    syncedAt = null
                )
            )
        }
    }
}

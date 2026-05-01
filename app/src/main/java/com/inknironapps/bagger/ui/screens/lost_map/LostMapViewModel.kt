package com.inknironapps.bagger.ui.screens.lost_map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.location.LocationProvider
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.LostDiscEventRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class LostMapUi(
    val unfound: List<LostDiscEventEntity> = emptyList(),
    val owned: Map<String, OwnedDiscEntity> = emptyMap(),
    val catalog: Map<String, DiscEntity> = emptyMap()
)

@HiltViewModel
class LostMapViewModel @Inject constructor(
    private val lostRepo: LostDiscEventRepository,
    private val ownedRepo: OwnedDiscRepository,
    catalogRepo: DiscCatalogRepository,
    private val location: LocationProvider
) : ViewModel() {

    val ui: StateFlow<LostMapUi> = combine(
        lostRepo.observeUnfound(),
        ownedRepo.observeAll(),
        catalogRepo.observeAll()
    ) { events, owned, catalog ->
        LostMapUi(events, owned.associateBy { it.id }, catalog.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LostMapUi())

    fun markLost(
        ownedDiscId: String,
        courseName: String?,
        hole: Int?,
        notes: String?,
        captureGps: Boolean
    ) {
        viewModelScope.launch {
            val pos = if (captureGps) location.current() else null
            lostRepo.upsert(
                LostDiscEventEntity(
                    id = UUID.randomUUID().toString(),
                    ownedDiscId = ownedDiscId,
                    lostAt = System.currentTimeMillis(),
                    lat = pos?.lat,
                    lng = pos?.lng,
                    courseName = courseName,
                    holeNumber = hole,
                    notes = notes,
                    foundAt = null
                )
            )
            val now = System.currentTimeMillis()
            ownedRepo.observeById(ownedDiscId).first()?.let {
                ownedRepo.upsert(it.copy(state = "Lost", updatedAt = now))
            }
        }
    }

    fun markFound(eventId: String) {
        viewModelScope.launch {
            val event = ui.value.unfound.firstOrNull { it.id == eventId } ?: return@launch
            lostRepo.upsert(event.copy(foundAt = System.currentTimeMillis()))
            ownedRepo.observeById(event.ownedDiscId).first()?.let {
                ownedRepo.upsert(it.copy(state = "Found", updatedAt = System.currentTimeMillis()))
            }
        }
    }
}

package com.inknironapps.bagger.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.LostDiscEventRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class StatsUi(
    val totalDiscs: Int = 0,
    val totalValue: Long = 0,
    val byBrand: Map<String, Int> = emptyMap(),
    val byType: Map<String, Int> = emptyMap(),
    val byPlastic: Map<String, Int> = emptyMap(),
    val lostThisYear: Int = 0,
    val retiredCount: Int = 0
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    ownedRepo: OwnedDiscRepository,
    catalogRepo: DiscCatalogRepository,
    lostRepo: LostDiscEventRepository
) : ViewModel() {

    val ui: StateFlow<StatsUi> = combine(
        ownedRepo.observeAll(),
        catalogRepo.observeAll(),
        lostRepo.observeUnfound()
    ) { owned, catalog, _ ->
        val byId = catalog.associateBy { it.id }
        val active = owned.filter { it.state !in setOf("Sold", "Traded", "Gifted") }
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yearStart = cal.timeInMillis
        StatsUi(
            totalDiscs = active.size,
            totalValue = owned.sumOf { it.purchasePrice ?: 0L },
            byBrand = active.groupingBy { byId[it.discId]?.brand ?: "Unknown" }.eachCount(),
            byType = active.groupingBy { byId[it.discId]?.discType ?: "Driver" }.eachCount(),
            byPlastic = active.filter { it.plasticType != null }
                .groupingBy { it.plasticType!! }.eachCount(),
            lostThisYear = owned.count { it.state == "Lost" && it.updatedAt >= yearStart },
            retiredCount = owned.count { it.state == "Retired" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUi())
}

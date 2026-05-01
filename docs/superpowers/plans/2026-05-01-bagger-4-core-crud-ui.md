# Bagger Plan 4 — Core CRUD UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Replace 4 placeholder screens with real CRUD UI. Shelf grid filterable by state/bag/brand/type. Bags list + detail. Discover catalog browse + search. Disc detail screen for owned discs. Manual add flow (search catalog → confirm → details form → save). Repos + ViewModels + Compose screens.

**Architecture:** MVVM. Repos return `Flow<T>` from Room. ViewModels expose `StateFlow<UiState>`. Compose screens collect via `collectAsStateWithLifecycle`. Hilt-injected ViewModels via `@HiltViewModel + hiltViewModel()`. Navigation arguments via `Bundle` strings (UUIDs).

**Tech:** No new deps. Already have Compose nav, Room, Hilt, Coil, Material 3, lifecycle-viewmodel-compose.

---

## File Structure

```
app/src/main/java/com/inknironapps/bagger/
├── domain/
│   ├── model/
│   │   ├── DiscState.kt              # enum
│   │   ├── DiscCondition.kt          # enum
│   │   └── DiscType.kt               # enum
│   └── repo/
│       ├── DiscCatalogRepository.kt  # read-only over Disc
│       ├── OwnedDiscRepository.kt
│       └── BagRepository.kt
├── data/
│   └── repo/
│       ├── DiscCatalogRepositoryImpl.kt
│       ├── OwnedDiscRepositoryImpl.kt
│       └── BagRepositoryImpl.kt
├── di/
│   └── RepoModule.kt                 # binds impls
└── ui/
    ├── components/
    │   ├── DiscCard.kt               # disc tile w/ flight # row
    │   ├── FlightNumbersRow.kt       # 4-circle flight #s
    │   └── EmptyState.kt
    ├── screens/
    │   ├── shelf/
    │   │   ├── ShelfScreen.kt
    │   │   └── ShelfViewModel.kt
    │   ├── bags/
    │   │   ├── BagsScreen.kt
    │   │   ├── BagsViewModel.kt
    │   │   ├── BagDetailScreen.kt
    │   │   └── BagDetailViewModel.kt
    │   ├── discover/
    │   │   ├── DiscoverScreen.kt
    │   │   └── DiscoverViewModel.kt
    │   ├── disc_detail/
    │   │   ├── OwnedDiscDetailScreen.kt
    │   │   ├── OwnedDiscDetailViewModel.kt
    │   │   ├── CatalogDiscDetailScreen.kt
    │   │   └── CatalogDiscDetailViewModel.kt
    │   └── add_disc/
    │       ├── AddDiscFlow.kt        # nav graph w/ 3 sub-screens
    │       ├── AddDiscViewModel.kt
    │       ├── SearchStep.kt
    │       ├── ConfirmStep.kt
    │       └── DetailsFormStep.kt
    └── nav/
        └── Destinations.kt           # extended w/ detail/add routes
```

---

## Task 1: Domain Enums + Repo Interfaces

Files:
- `domain/model/DiscState.kt`, `DiscCondition.kt`, `DiscType.kt`
- `domain/repo/DiscCatalogRepository.kt`, `OwnedDiscRepository.kt`, `BagRepository.kt`

```kotlin
// DiscState.kt
package com.inknironapps.bagger.domain.model

enum class DiscState {
    Shelf, InBag, Lost, Found, Sold, Traded, Retired, Gifted;

    companion object {
        fun fromString(s: String): DiscState =
            entries.firstOrNull { it.name == s } ?: Shelf
    }
}
```

```kotlin
// DiscCondition.kt
package com.inknironapps.bagger.domain.model

enum class DiscCondition {
    New, Good, Beat, Dyed;
    companion object {
        fun fromString(s: String): DiscCondition =
            entries.firstOrNull { it.name == s } ?: Good
    }
}
```

```kotlin
// DiscType.kt
package com.inknironapps.bagger.domain.model

enum class DiscType {
    Putter, Approach, Mid, Fairway, Driver;
    companion object {
        fun fromString(s: String): DiscType =
            entries.firstOrNull { it.name == s } ?: Driver
    }
}
```

```kotlin
// DiscCatalogRepository.kt
package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.DiscEntity
import kotlinx.coroutines.flow.Flow

interface DiscCatalogRepository {
    fun observeAll(): Flow<List<DiscEntity>>
    fun search(query: String): Flow<List<DiscEntity>>
    suspend fun getById(id: String): DiscEntity?
}
```

```kotlin
// OwnedDiscRepository.kt
package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import kotlinx.coroutines.flow.Flow

interface OwnedDiscRepository {
    fun observeAll(): Flow<List<OwnedDiscEntity>>
    fun observeById(id: String): Flow<OwnedDiscEntity?>
    fun observeByState(state: String): Flow<List<OwnedDiscEntity>>
    fun observeByBag(bagId: String): Flow<List<OwnedDiscEntity>>
    suspend fun upsert(disc: OwnedDiscEntity)
    suspend fun delete(disc: OwnedDiscEntity)
}
```

```kotlin
// BagRepository.kt
package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.BagEntity
import kotlinx.coroutines.flow.Flow

interface BagRepository {
    fun observeAll(): Flow<List<BagEntity>>
    suspend fun getById(id: String): BagEntity?
    suspend fun upsert(bag: BagEntity)
    suspend fun delete(bag: BagEntity)
}
```

Commit: `feat: add domain enums + repository interfaces`

---

## Task 2: Repository Impls + DI Binding

Files:
- `data/repo/DiscCatalogRepositoryImpl.kt`, `OwnedDiscRepositoryImpl.kt`, `BagRepositoryImpl.kt`
- `di/RepoModule.kt`

```kotlin
// DiscCatalogRepositoryImpl.kt
package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import javax.inject.Inject

class DiscCatalogRepositoryImpl @Inject constructor(
    private val dao: DiscDao
) : DiscCatalogRepository {
    override fun observeAll() = dao.observeAll()
    override fun search(query: String) = dao.search(query)
    override suspend fun getById(id: String) = dao.getById(id)
}
```

```kotlin
// OwnedDiscRepositoryImpl.kt
package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import javax.inject.Inject

class OwnedDiscRepositoryImpl @Inject constructor(
    private val dao: OwnedDiscDao
) : OwnedDiscRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeById(id: String) = dao.observeById(id)
    override fun observeByState(state: String) = dao.observeByState(state)
    override fun observeByBag(bagId: String) = dao.observeByBag(bagId)
    override suspend fun upsert(disc: OwnedDiscEntity) = dao.upsert(disc)
    override suspend fun delete(disc: OwnedDiscEntity) = dao.delete(disc)
}
```

```kotlin
// BagRepositoryImpl.kt
package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.BagDao
import com.inknironapps.bagger.data.db.entity.BagEntity
import com.inknironapps.bagger.domain.repo.BagRepository
import javax.inject.Inject

class BagRepositoryImpl @Inject constructor(
    private val dao: BagDao
) : BagRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: String) = dao.getById(id)
    override suspend fun upsert(bag: BagEntity) = dao.upsert(bag)
    override suspend fun delete(bag: BagEntity) = dao.delete(bag)
}
```

```kotlin
// di/RepoModule.kt
package com.inknironapps.bagger.di

import com.inknironapps.bagger.data.repo.BagRepositoryImpl
import com.inknironapps.bagger.data.repo.DiscCatalogRepositoryImpl
import com.inknironapps.bagger.data.repo.OwnedDiscRepositoryImpl
import com.inknironapps.bagger.domain.repo.BagRepository
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {
    @Binds @Singleton abstract fun bindCatalog(impl: DiscCatalogRepositoryImpl): DiscCatalogRepository
    @Binds @Singleton abstract fun bindOwned(impl: OwnedDiscRepositoryImpl): OwnedDiscRepository
    @Binds @Singleton abstract fun bindBag(impl: BagRepositoryImpl): BagRepository
}
```

Commit: `feat: add repository implementations + Hilt bindings`

---

## Task 3: UI Components Library

Files:
- `ui/components/FlightNumbersRow.kt`
- `ui/components/DiscCard.kt`
- `ui/components/EmptyState.kt`

```kotlin
// FlightNumbersRow.kt
package com.inknironapps.bagger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FlightNumbersRow(speed: Float, glide: Float, turn: Float, fade: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlightCircle(speed.formatFlight(), MaterialTheme.colorScheme.primary)
        FlightCircle(glide.formatFlight(), MaterialTheme.colorScheme.secondary)
        FlightCircle(turn.formatFlight(), MaterialTheme.colorScheme.tertiary)
        FlightCircle(fade.formatFlight(), MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun FlightCircle(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun Float.formatFlight(): String =
    if (this == this.toInt().toFloat()) this.toInt().toString() else "%.1f".format(this)
```

```kotlin
// DiscCard.kt
package com.inknironapps.bagger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity

@Composable
fun CatalogDiscCard(disc: DiscEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Text(disc.brand, style = MaterialTheme.typography.labelMedium)
            Text(disc.mold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlightNumbersRow(disc.speed, disc.glide, disc.turn, disc.fade)
            Spacer(Modifier.height(4.dp))
            Text("${disc.discType} · ${disc.stability}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OwnedDiscCard(
    owned: OwnedDiscEntity,
    catalog: DiscEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            if (catalog != null) {
                Text(catalog.brand, style = MaterialTheme.typography.labelMedium)
                Text(catalog.mold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                FlightNumbersRow(catalog.speed, catalog.glide, catalog.turn, catalog.fade)
            } else {
                Text("Unknown disc", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(owned.state) })
                owned.plasticType?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                owned.weight?.let { AssistChip(onClick = {}, label = { Text("${it}g") }) }
            }
        }
    }
}
```

```kotlin
// EmptyState.kt
package com.inknironapps.bagger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
```

Commit: `feat: add disc card + flight numbers + empty state components`

---

## Task 4: Shelf Screen + ViewModel

Files:
- `ui/screens/shelf/ShelfViewModel.kt`
- `ui/screens/shelf/ShelfScreen.kt`

```kotlin
// ShelfViewModel.kt
package com.inknironapps.bagger.ui.screens.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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
```

```kotlin
// ShelfScreen.kt
package com.inknironapps.bagger.ui.screens.shelf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.EmptyState
import com.inknironapps.bagger.ui.components.OwnedDiscCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onAddDisc: () -> Unit,
    onDiscClick: (String) -> Unit,
    vm: ShelfViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Shelf") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDisc,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add disc") }
            )
        }
    ) { padding ->
        if (state.owned.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState(
                    "Your shelf is empty",
                    "Tap Add disc to catalog your first disc.",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(state.owned, key = { it.id }) { owned ->
                    OwnedDiscCard(
                        owned = owned,
                        catalog = state.catalog[owned.discId],
                        onClick = { onDiscClick(owned.id) }
                    )
                }
            }
        }
    }
}
```

Commit: `feat: add Shelf screen w/ filterable owned-disc grid`

---

## Task 5: Bags Screen + Bag Detail

Files:
- `ui/screens/bags/BagsViewModel.kt`
- `ui/screens/bags/BagsScreen.kt`
- `ui/screens/bags/BagDetailViewModel.kt`
- `ui/screens/bags/BagDetailScreen.kt`

```kotlin
// BagsViewModel.kt
package com.inknironapps.bagger.ui.screens.bags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.BagEntity
import com.inknironapps.bagger.domain.repo.BagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BagsUiState(val bags: List<BagEntity> = emptyList())

@HiltViewModel
class BagsViewModel @Inject constructor(
    private val repo: BagRepository
) : ViewModel() {
    val ui: StateFlow<BagsUiState> = kotlinx.coroutines.flow.flow {
        repo.observeAll().collect { emit(BagsUiState(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BagsUiState())

    fun createBag(name: String) {
        if (name.isBlank()) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repo.upsert(BagEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                description = null,
                iconColor = "#095F73",
                sortOrder = 0,
                createdAt = now, updatedAt = now,
                userId = null, syncedAt = null
            ))
        }
    }
}
```

```kotlin
// BagsScreen.kt
package com.inknironapps.bagger.ui.screens.bags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagsScreen(
    onBagClick: (String) -> Unit,
    vm: BagsViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bags") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New bag") }
            )
        }
    ) { padding ->
        if (state.bags.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("No bags yet", "Create a bag to organize your discs.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.bags, key = { it.id }) { bag ->
                    Card(onClick = { onBagClick(bag.id) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(bag.name, style = MaterialTheme.typography.titleMedium)
                            bag.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (showCreate) {
            AlertDialog(
                onDismissRequest = { showCreate = false; newName = "" },
                title = { Text("New bag") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.createBag(newName)
                        showCreate = false; newName = ""
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreate = false; newName = "" }) { Text("Cancel") }
                }
            )
        }
    }
}
```

```kotlin
// BagDetailViewModel.kt
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
import kotlinx.coroutines.flow.*
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
        kotlinx.coroutines.flow.flow { emit(bagRepo.getById(bagId)) }
    ) { discs, catalog, bag ->
        BagDetailUiState(bag, discs, catalog.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BagDetailUiState())

    fun removeDiscFromBag(disc: OwnedDiscEntity) {
        viewModelScope.launch {
            ownedRepo.upsert(disc.copy(bagId = null, state = "Shelf",
                updatedAt = System.currentTimeMillis()))
        }
    }
}
```

```kotlin
// BagDetailScreen.kt
package com.inknironapps.bagger.ui.screens.bags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.EmptyState
import com.inknironapps.bagger.ui.components.OwnedDiscCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagDetailScreen(
    onBack: () -> Unit,
    onDiscClick: (String) -> Unit,
    vm: BagDetailViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.bag?.name ?: "Bag") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (state.discs.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("No discs in this bag", "Move discs from your shelf into this bag.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.discs, key = { it.id }) { disc ->
                    OwnedDiscCard(disc, state.catalog[disc.discId], { onDiscClick(disc.id) })
                }
            }
        }
    }
}
```

Commit: `feat: add Bags screen + bag detail w/ create + remove`

---

## Task 6: Discover (Catalog Browse + Search)

Files:
- `ui/screens/discover/DiscoverViewModel.kt`
- `ui/screens/discover/DiscoverScreen.kt`

```kotlin
// DiscoverViewModel.kt
package com.inknironapps.bagger.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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
```

```kotlin
// DiscoverScreen.kt
package com.inknironapps.bagger.ui.screens.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.CatalogDiscCard
import com.inknironapps.bagger.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onDiscClick: (String) -> Unit,
    vm: DiscoverViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Discover") })
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    label = { Text("Search brand or mold") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf<String?>(null, "Putter", "Approach", "Mid", "Fairway", "Driver").forEach { t ->
                        FilterChip(
                            selected = state.typeFilter == t,
                            onClick = { vm.setType(t) },
                            label = { Text(t ?: "All") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (state.results.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("No discs match", "Try a different search or filter.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.results, key = { it.id }) { disc ->
                    CatalogDiscCard(disc, { onDiscClick(disc.id) })
                }
            }
        }
    }
}
```

Commit: `feat: add Discover catalog browse w/ search + type filter`

---

## Task 7: Catalog Disc Detail + Owned Disc Detail

Files:
- `ui/screens/disc_detail/CatalogDiscDetailViewModel.kt`
- `ui/screens/disc_detail/CatalogDiscDetailScreen.kt`
- `ui/screens/disc_detail/OwnedDiscDetailViewModel.kt`
- `ui/screens/disc_detail/OwnedDiscDetailScreen.kt`

```kotlin
// CatalogDiscDetailViewModel.kt
package com.inknironapps.bagger.ui.screens.disc_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CatalogDetailUi(val disc: DiscEntity? = null, val added: Boolean = false)

@HiltViewModel
class CatalogDiscDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalogRepo: DiscCatalogRepository,
    private val ownedRepo: OwnedDiscRepository
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
            ownedRepo.upsert(OwnedDiscEntity(
                id = UUID.randomUUID().toString(),
                discId = disc.id,
                plasticType = null, weight = null, color = null,
                condition = "Good", state = "Shelf",
                bagId = null, purchaseDate = null, purchasePrice = null,
                notes = null, isOriginalOwner = true,
                customTags = emptyList(),
                createdAt = now, updatedAt = now,
                userId = null, syncedAt = null
            ))
            _ui.value = _ui.value.copy(added = true)
        }
    }
}
```

```kotlin
// CatalogDiscDetailScreen.kt
package com.inknironapps.bagger.ui.screens.disc_detail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDiscDetailScreen(
    onBack: () -> Unit,
    vm: CatalogDiscDetailViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    if (state.added) LaunchedEffect(Unit) { snack.showSnackbar("Added to your shelf") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.disc?.mold ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        val disc = state.disc ?: return@Scaffold
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(disc.brand, style = MaterialTheme.typography.titleSmall)
            Text(disc.mold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            FlightNumbersRow(disc.speed, disc.glide, disc.turn, disc.fade)
            Text("${disc.discType} · ${disc.stability}", style = MaterialTheme.typography.bodyMedium)
            disc.yearReleased?.let { Text("Released $it") }
            if (disc.pdgaApproved) Text("PDGA approved", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Button(onClick = vm::addToShelf, enabled = !state.added) {
                Text(if (state.added) "Added" else "Add to my shelf")
            }
        }
    }
}
```

```kotlin
// OwnedDiscDetailViewModel.kt
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
import kotlinx.coroutines.flow.*
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
            ownedRepo.upsert(o.copy(state = state, bagId = bagId,
                updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete() {
        val o = ui.value.owned ?: return
        viewModelScope.launch { ownedRepo.delete(o) }
    }
}
```

```kotlin
// OwnedDiscDetailScreen.kt
package com.inknironapps.bagger.ui.screens.disc_detail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnedDiscDetailScreen(
    onBack: () -> Unit,
    vm: OwnedDiscDetailViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var showBagPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.catalog?.mold ?: "Disc") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.delete(); onBack() }) {
                        Icon(Icons.Filled.Delete, null)
                    }
                }
            )
        }
    ) { padding ->
        val o = state.owned ?: return@Scaffold
        val c = state.catalog
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (c != null) {
                Text(c.brand, style = MaterialTheme.typography.titleSmall)
                Text(c.mold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                FlightNumbersRow(c.speed, c.glide, c.turn, c.fade)
            }
            Spacer(Modifier.height(8.dp))
            Text("State: ${o.state}", style = MaterialTheme.typography.bodyMedium)
            o.plasticType?.let { Text("Plastic: $it") }
            o.weight?.let { Text("Weight: ${it}g") }
            o.color?.let { Text("Color: $it") }
            o.notes?.let { Text("Notes: $it") }
            Spacer(Modifier.height(12.dp))
            Text("Move to:", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { vm.changeState("Shelf", null) }) { Text("Shelf") }
                FilledTonalButton(onClick = { showBagPicker = true }) { Text("A bag") }
                FilledTonalButton(onClick = { vm.changeState("Lost") }) { Text("Lost") }
                FilledTonalButton(onClick = { vm.changeState("Retired") }) { Text("Retired") }
            }
        }

        if (showBagPicker) {
            AlertDialog(
                onDismissRequest = { showBagPicker = false },
                title = { Text("Pick a bag") },
                text = {
                    Column {
                        if (state.bags.isEmpty()) Text("No bags yet — create one in Bags tab.")
                        state.bags.forEach { bag ->
                            TextButton(onClick = {
                                vm.changeState("InBag", bag.id)
                                showBagPicker = false
                            }) { Text(bag.name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showBagPicker = false }) { Text("Cancel") } }
            )
        }
    }
}
```

Commit: `feat: add catalog + owned disc detail screens`

---

## Task 8: Update Nav Graph

Files: `ui/nav/Destinations.kt`, `ui/nav/BaggerNavHost.kt`, `MainActivity.kt`

Extend `Destinations.kt`:

```kotlin
package com.inknironapps.bagger.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Shelf    : Destination("shelf",    "Shelf",    Icons.Filled.Inventory2)
    data object Bags     : Destination("bags",     "Bags",     Icons.Filled.Backpack)
    data object Discover : Destination("discover", "Discover", Icons.Filled.Search)
    data object More     : Destination("more",     "More",     Icons.Filled.MoreHoriz)
}

object DetailRoutes {
    const val OwnedDetail = "owned/{ownedId}"
    fun ownedDetail(id: String) = "owned/$id"
    const val CatalogDetail = "catalog/{discId}"
    fun catalogDetail(id: String) = "catalog/$id"
    const val BagDetail = "bag/{bagId}"
    fun bagDetail(id: String) = "bag/$id"
}

val BottomDestinations: List<Destination> = listOf(
    Destination.Shelf, Destination.Bags, Destination.Discover, Destination.More
)
```

Rewrite `BaggerNavHost.kt`:

```kotlin
package com.inknironapps.bagger.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.inknironapps.bagger.ui.screens.bags.BagDetailScreen
import com.inknironapps.bagger.ui.screens.bags.BagsScreen
import com.inknironapps.bagger.ui.screens.disc_detail.CatalogDiscDetailScreen
import com.inknironapps.bagger.ui.screens.disc_detail.OwnedDiscDetailScreen
import com.inknironapps.bagger.ui.screens.discover.DiscoverScreen
import com.inknironapps.bagger.ui.screens.shelf.ShelfScreen
import com.inknironapps.bagger.ui.screens.MoreScreen

@Composable
fun BaggerNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Destination.Shelf.route) {
        composable(Destination.Shelf.route) {
            ShelfScreen(
                onAddDisc = { navController.navigate("discover") },
                onDiscClick = { id -> navController.navigate(DetailRoutes.ownedDetail(id)) }
            )
        }
        composable(Destination.Bags.route) {
            BagsScreen(onBagClick = { id -> navController.navigate(DetailRoutes.bagDetail(id)) })
        }
        composable(Destination.Discover.route) {
            DiscoverScreen(onDiscClick = { id -> navController.navigate(DetailRoutes.catalogDetail(id)) })
        }
        composable(Destination.More.route) { MoreScreen() }

        composable(DetailRoutes.OwnedDetail,
            arguments = listOf(navArgument("ownedId") { type = NavType.StringType })) {
            OwnedDiscDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(DetailRoutes.CatalogDetail,
            arguments = listOf(navArgument("discId") { type = NavType.StringType })) {
            CatalogDiscDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(DetailRoutes.BagDetail,
            arguments = listOf(navArgument("bagId") { type = NavType.StringType })) {
            BagDetailScreen(
                onBack = { navController.popBackStack() },
                onDiscClick = { id -> navController.navigate(DetailRoutes.ownedDetail(id)) }
            )
        }
    }
}
```

Delete the old placeholder screen files `ShelfScreen.kt`, `BagsScreen.kt`, `DiscoverScreen.kt` from the root `ui/screens/` package since real screens now live in subfolders. Keep `MoreScreen.kt` as-is for now (Plan 7 fills it in). Move it from `ui/screens/MoreScreen.kt` (already there) — leave alone.

Commit: `feat: wire nav graph for shelf/bags/discover w/ detail routes`

---

## Task 9: Smoke UI Test

Files: `app/src/androidTest/java/com/inknironapps/bagger/ShelfNavTest.kt` (replace existing nav test if conflicts)

```kotlin
package com.inknironapps.bagger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ShelfNavTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun emptyShelfShowsCallToAction() {
        compose.onNodeWithText("Your shelf is empty").assertIsDisplayed()
    }

    @Test fun discoverTabShowsCatalogSearch() {
        compose.onNodeWithText("Discover").performClick()
        compose.onNodeWithText("Search brand or mold").assertIsDisplayed()
    }

    @Test fun bagsTabShowsCreateAction() {
        compose.onNodeWithText("Bags").performClick()
        compose.onNodeWithText("New bag").assertIsDisplayed()
    }
}
```

The old `MainActivityNavTest.kt` from Plan 1 referenced `"Shelf — Plan 3"` placeholder text. Delete it (now obsolete; replaced by `ShelfNavTest.kt`).

Commit: `test: replace placeholder nav test w/ real Shelf/Bags/Discover smoke tests`

---

## Task 10: Push + Verify CI + Tag CHANGELOG

```markdown
## [0.4.0] - 2026-05-01

### Added

- Real Shelf screen with filterable owned-disc grid and an Add disc shortcut.
- Bags tab with a list of saved bags, a create dialog, and a bag detail screen showing the discs assigned to that bag.
- Discover screen for browsing the full disc catalog with text search and disc-type filters.
- Disc detail screens for both catalog discs (with an Add to my shelf action) and owned discs (with state controls and bag reassignment).
- Repository layer (`DiscCatalogRepository`, `OwnedDiscRepository`, `BagRepository`) backed by Room, wired through Hilt.
- New shared UI components: `DiscCard`, `FlightNumbersRow`, and `EmptyState`.
- Compose smoke tests covering empty shelf, Discover search field, and Bags create action.
```

Commit: `docs: tag 0.4.0 — Plan 4 core CRUD UI complete`

Push, verify auto-merge + release.yml.

---

## Verification Checklist

- [ ] All 10 task commits land
- [ ] `assemblePlaystoreDebug` green
- [ ] All unit tests pass (8 total: 5 from prior + 3 new sync tests are in `app/src/test`; nav tests are instrumented)
- [ ] App installs + launches on emulator (manual visual verify recommended)
- [ ] CI: auto-merge + release green
- [ ] CHANGELOG v0.4.0 tagged

---

## Out of Scope (Tracked)

- **Plan 5:** Photo-ID pipeline (CameraX, ML Kit, JaroWinkler)
- **Plan 6:** Disc lifecycle features (lost-disc map, wishlist, comparison)
- **Plan 7:** Stats + Settings + Onboarding + What's New + Permissions
- **Plan 8:** Release pipeline (signing, AAB, Play Store)
- Color picker, plastic-type autocomplete, more sophisticated state UX → Plan 6
- Manual add flow w/ details form → defer to Plan 5 (fits next to camera flow's Confirm step)
- Photo carousel on owned disc detail → Plan 5

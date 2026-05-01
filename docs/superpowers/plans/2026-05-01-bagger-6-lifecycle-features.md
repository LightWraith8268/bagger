# Bagger Plan 6 — Lifecycle Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Wishlist (add catalog disc → wishlist → convert to owned). Lost-disc tracking with optional GPS pin and a Lost Map screen using Google Maps Compose. Disc comparison view with flight-chart Canvas overlay (2–3 discs side by side). More tab becomes a real menu wiring Wishlist, Lost Map, Comparison, Stats (Plan 7), Settings (Plan 7).

**Architecture:** New repos for wishlist + lost events. New screens. Map uses `maps-compose` + Play Services. Without `MAPS_API_KEY` the map renders a watermarked dev view — fine for now. Comparison uses Compose Canvas (no third-party chart lib).

**Tech:** `maps-compose 6.4.4` + `play-services-maps 19.0.0` + `play-services-location 21.3.0`. No new test deps.

---

## File Structure

```
app/src/main/java/com/inknironapps/bagger/
├── domain/repo/
│   ├── WishlistRepository.kt
│   └── LostDiscEventRepository.kt
├── data/repo/
│   ├── WishlistRepositoryImpl.kt
│   └── LostDiscEventRepositoryImpl.kt
├── data/location/
│   └── LocationProvider.kt        # FusedLocationProviderClient wrapper
└── ui/screens/
    ├── wishlist/
    │   ├── WishlistViewModel.kt
    │   └── WishlistScreen.kt
    ├── lost_map/
    │   ├── LostMapViewModel.kt
    │   ├── LostMapScreen.kt
    │   └── MarkLostDialog.kt
    ├── comparison/
    │   ├── ComparisonViewModel.kt
    │   ├── ComparisonScreen.kt
    │   └── FlightChartCanvas.kt
    └── more/
        └── MoreScreen.kt          # rewrite as menu
```

---

## Task 1: Deps + Manifest

- [ ] Add to `gradle/libs.versions.toml` `[versions]`:
```toml
maps-compose = "6.4.4"
play-services-maps = "19.0.0"
play-services-location = "21.3.0"
```

- [ ] Add to `[libraries]`:
```toml
maps-compose = { module = "com.google.maps.android:maps-compose", version.ref = "maps-compose" }
play-services-maps = { module = "com.google.android.gms:play-services-maps", version.ref = "play-services-maps" }
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "play-services-location" }
```

- [ ] Add deps:
```kotlin
implementation(libs.maps.compose)
implementation(libs.play.services.maps)
implementation(libs.play.services.location)
```

- [ ] Add to `AndroidManifest.xml` — permissions:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

- [ ] Add inside `<application>` (above closing tag):
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

- [ ] Add to `app/build.gradle.kts` `defaultConfig`:
```kotlin
manifestPlaceholders["MAPS_API_KEY"] = providers.gradleProperty("MAPS_API_KEY").getOrElse("")
```

The `MAPS_API_KEY` resolves from `local.properties` or env. With empty value, map shows watermarked dev view but works for testing. Production needs a real key set in CI secrets.

- [ ] Build green.

Commit: `chore: add Maps Compose + Play Services + location permissions`

---

## Task 2: Repos for Wishlist + LostDiscEvent

Files:
- `domain/repo/WishlistRepository.kt`
- `domain/repo/LostDiscEventRepository.kt`
- `data/repo/WishlistRepositoryImpl.kt`
- `data/repo/LostDiscEventRepositoryImpl.kt`
- Update `di/RepoModule.kt` w/ new bindings

```kotlin
// WishlistRepository.kt
package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun observeAll(): Flow<List<WishlistItemEntity>>
    suspend fun upsert(item: WishlistItemEntity)
    suspend fun delete(item: WishlistItemEntity)
}
```

```kotlin
// LostDiscEventRepository.kt
package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import kotlinx.coroutines.flow.Flow

interface LostDiscEventRepository {
    fun observeUnfound(): Flow<List<LostDiscEventEntity>>
    fun observeForDisc(discId: String): Flow<List<LostDiscEventEntity>>
    suspend fun upsert(event: LostDiscEventEntity)
}
```

```kotlin
// WishlistRepositoryImpl.kt
package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.WishlistDao
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import com.inknironapps.bagger.domain.repo.WishlistRepository
import javax.inject.Inject

class WishlistRepositoryImpl @Inject constructor(private val dao: WishlistDao) : WishlistRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun upsert(item: WishlistItemEntity) = dao.upsert(item)
    override suspend fun delete(item: WishlistItemEntity) = dao.delete(item)
}
```

```kotlin
// LostDiscEventRepositoryImpl.kt
package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.LostDiscEventDao
import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import com.inknironapps.bagger.domain.repo.LostDiscEventRepository
import javax.inject.Inject

class LostDiscEventRepositoryImpl @Inject constructor(
    private val dao: LostDiscEventDao
) : LostDiscEventRepository {
    override fun observeUnfound() = dao.observeUnfound()
    override fun observeForDisc(discId: String) = dao.observeForDisc(discId)
    override suspend fun upsert(event: LostDiscEventEntity) = dao.upsert(event)
}
```

Update `di/RepoModule.kt`:
```kotlin
@Binds @Singleton abstract fun bindWishlist(impl: WishlistRepositoryImpl): WishlistRepository
@Binds @Singleton abstract fun bindLost(impl: LostDiscEventRepositoryImpl): LostDiscEventRepository
```

Commit: `feat: add wishlist + lost-disc-event repositories`

---

## Task 3: LocationProvider

Files: `data/location/LocationProvider.kt`

```kotlin
package com.inknironapps.bagger.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LatLng(val lat: Double, val lng: Double)

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): LatLng? {
        if (!hasPermission()) return null
        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    cont.resume(loc?.let { LatLng(it.latitude, it.longitude) })
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}
```

Commit: `feat: add LocationProvider w/ FusedLocationProviderClient`

---

## Task 4: Wishlist Screen

Files: `ui/screens/wishlist/WishlistViewModel.kt`, `WishlistScreen.kt`

```kotlin
// WishlistViewModel.kt
package com.inknironapps.bagger.ui.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import com.inknironapps.bagger.domain.repo.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WishlistUi(
    val items: List<WishlistItemEntity> = emptyList(),
    val catalog: Map<String, DiscEntity> = emptyMap()
)

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val wishlistRepo: WishlistRepository,
    private val catalogRepo: DiscCatalogRepository,
    private val ownedRepo: OwnedDiscRepository
) : ViewModel() {

    val ui: StateFlow<WishlistUi> = combine(
        wishlistRepo.observeAll(),
        catalogRepo.observeAll()
    ) { items, catalog -> WishlistUi(items, catalog.associateBy { it.id }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishlistUi())

    fun add(disc: DiscEntity) {
        viewModelScope.launch {
            wishlistRepo.upsert(WishlistItemEntity(
                id = UUID.randomUUID().toString(),
                discId = disc.id,
                addedAt = System.currentTimeMillis(),
                targetWeight = null,
                targetPlastic = null,
                notes = null
            ))
        }
    }

    fun remove(item: WishlistItemEntity) { viewModelScope.launch { wishlistRepo.delete(item) } }

    fun convertToOwned(item: WishlistItemEntity) {
        val disc = ui.value.catalog[item.discId] ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            ownedRepo.upsert(OwnedDiscEntity(
                id = UUID.randomUUID().toString(),
                discId = disc.id,
                plasticType = item.targetPlastic,
                weight = item.targetWeight,
                color = null,
                condition = "New",
                state = "Shelf",
                bagId = null,
                purchaseDate = now,
                purchasePrice = null,
                notes = item.notes,
                isOriginalOwner = true,
                customTags = emptyList(),
                createdAt = now, updatedAt = now,
                userId = null, syncedAt = null
            ))
            wishlistRepo.delete(item)
        }
    }
}
```

```kotlin
// WishlistScreen.kt
package com.inknironapps.bagger.ui.screens.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.EmptyState
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(onBack: () -> Unit, vm: WishlistViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wishlist") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("No wishlist items", "Add discs from Discover to track what you want.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    val disc = state.catalog[item.discId]
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(disc?.brand ?: "Unknown", style = MaterialTheme.typography.labelMedium)
                            Text(disc?.mold ?: item.discId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            disc?.let {
                                Spacer(Modifier.height(4.dp))
                                FlightNumbersRow(it.speed, it.glide, it.turn, it.fade)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { vm.convertToOwned(item) }) { Text("Bought it") }
                                OutlinedButton(onClick = { vm.remove(item) }) {
                                    Icon(Icons.Filled.Delete, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Also: add wishlist add button to `CatalogDiscDetailViewModel`/`CatalogDiscDetailScreen` from Plan 4. Inject `WishlistRepository`, add `addToWishlist()` method, render an "Add to wishlist" outlined button next to "Add to my shelf".

Commit: `feat: add Wishlist screen + add-to-wishlist on catalog detail`

---

## Task 5: Lost Map + Mark Lost Flow

Files: `ui/screens/lost_map/MarkLostDialog.kt`, `LostMapViewModel.kt`, `LostMapScreen.kt`

```kotlin
// MarkLostDialog.kt
package com.inknironapps.bagger.ui.screens.lost_map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkLostDialog(
    onDismiss: () -> Unit,
    onConfirm: (courseName: String?, hole: Int?, notes: String?, captureGps: Boolean) -> Unit
) {
    var courseName by remember { mutableStateOf("") }
    var hole by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var capture by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark disc lost") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(courseName, { courseName = it }, label = { Text("Course name (optional)") }, singleLine = true)
                OutlinedTextField(hole, { hole = it }, label = { Text("Hole number (optional)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = capture, onCheckedChange = { capture = it })
                    Text("Pin GPS location")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    courseName.takeIf { it.isNotBlank() },
                    hole.toIntOrNull(),
                    notes.takeIf { it.isNotBlank() },
                    capture
                )
            }) { Text("Mark lost") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

```kotlin
// LostMapViewModel.kt
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
import kotlinx.coroutines.flow.*
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
    private val catalogRepo: DiscCatalogRepository,
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
            lostRepo.upsert(LostDiscEventEntity(
                id = UUID.randomUUID().toString(),
                ownedDiscId = ownedDiscId,
                lostAt = System.currentTimeMillis(),
                lat = pos?.lat,
                lng = pos?.lng,
                courseName = courseName,
                holeNumber = hole,
                notes = notes,
                foundAt = null
            ))
            // Also flip the owned disc state to "Lost"
            val now = System.currentTimeMillis()
            ownedRepo.observeById(ownedDiscId).first()?.let {
                ownedRepo.upsert(it.copy(state = "Lost", updatedAt = now))
            }
        }
    }

    fun markFound(eventId: String) {
        viewModelScope.launch {
            // find via flatMap of unfound (cheap since list is small)
            val event = ui.value.unfound.firstOrNull { it.id == eventId } ?: return@launch
            lostRepo.upsert(event.copy(foundAt = System.currentTimeMillis()))
            // Also flip owned state to Found
            ownedRepo.observeById(event.ownedDiscId).first()?.let {
                ownedRepo.upsert(it.copy(state = "Found", updatedAt = System.currentTimeMillis()))
            }
        }
    }
}
```

```kotlin
// LostMapScreen.kt
package com.inknironapps.bagger.ui.screens.lost_map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.inknironapps.bagger.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostMapScreen(onBack: () -> Unit, vm: LostMapViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val withGps = state.unfound.filter { it.lat != null && it.lng != null }
    val withoutGps = state.unfound.filter { it.lat == null || it.lng == null }

    val center = withGps.firstOrNull()?.let { LatLng(it.lat!!, it.lng!!) } ?: LatLng(40.0, -100.0)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, if (withGps.isEmpty()) 3f else 12f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lost Discs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (state.unfound.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("No lost discs", "Discs marked Lost from the disc detail screen show up here.")
            }
        } else {
            Column(Modifier.padding(padding)) {
                Box(Modifier.weight(1f)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        withGps.forEach { event ->
                            val owned = state.owned[event.ownedDiscId]
                            val disc = owned?.let { state.catalog[it.discId] }
                            Marker(
                                state = MarkerState(LatLng(event.lat!!, event.lng!!)),
                                title = disc?.let { "${it.brand} ${it.mold}" } ?: "Disc",
                                snippet = listOfNotNull(event.courseName, event.holeNumber?.let { "Hole $it" }).joinToString(" · ")
                            )
                        }
                    }
                }

                if (withoutGps.isNotEmpty()) {
                    Text("Without GPS pin", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(withoutGps, key = { it.id }) { e ->
                            val owned = state.owned[e.ownedDiscId]
                            val disc = owned?.let { state.catalog[it.discId] }
                            Card {
                                Column(Modifier.padding(8.dp)) {
                                    Text(disc?.let { "${it.brand} ${it.mold}" } ?: "Disc")
                                    e.courseName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    TextButton(onClick = { vm.markFound(e.id) }) { Text("Mark found") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Wire `MarkLostDialog` into `OwnedDiscDetailScreen` from Plan 4. Replace the `vm.changeState("Lost")` quick-button with `showMarkLostDialog = true`. On confirm, call `vm.markLost(...)` (need to inject `LostDiscEventRepository` + `LocationProvider` into `OwnedDiscDetailViewModel`).

Add to `OwnedDiscDetailViewModel`:

```kotlin
@HiltViewModel
class OwnedDiscDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ownedRepo: OwnedDiscRepository,
    private val catalogRepo: DiscCatalogRepository,
    private val lostRepo: LostDiscEventRepository,
    private val location: LocationProvider,
    bagRepo: BagRepository
) : ViewModel() {
    // ... existing

    fun markLost(courseName: String?, hole: Int?, notes: String?, captureGps: Boolean) {
        val o = ui.value.owned ?: return
        viewModelScope.launch {
            val pos = if (captureGps) location.current() else null
            lostRepo.upsert(LostDiscEventEntity(
                id = UUID.randomUUID().toString(),
                ownedDiscId = o.id,
                lostAt = System.currentTimeMillis(),
                lat = pos?.lat,
                lng = pos?.lng,
                courseName = courseName,
                holeNumber = hole,
                notes = notes,
                foundAt = null
            ))
            ownedRepo.upsert(o.copy(state = "Lost", updatedAt = System.currentTimeMillis()))
        }
    }
}
```

Update `OwnedDiscDetailScreen` to show dialog when "Lost" button tapped:

```kotlin
var showLostDialog by remember { mutableStateOf(false) }
// replace the existing Lost button:
FilledTonalButton(onClick = { showLostDialog = true }) { Text("Lost") }
// at bottom of screen:
if (showLostDialog) {
    MarkLostDialog(
        onDismiss = { showLostDialog = false },
        onConfirm = { course, hole, notes, gps ->
            vm.markLost(course, hole, notes, gps)
            showLostDialog = false
        }
    )
}
```

Commit: `feat: add Lost Map + Mark Lost flow w/ optional GPS`

---

## Task 6: Disc Comparison

Files: `ui/screens/comparison/ComparisonViewModel.kt`, `FlightChartCanvas.kt`, `ComparisonScreen.kt`

```kotlin
// ComparisonViewModel.kt
package com.inknironapps.bagger.ui.screens.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class CompareUi(
    val all: List<DiscEntity> = emptyList(),
    val picked: List<DiscEntity> = emptyList(),
    val query: String = ""
)

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    private val catalogRepo: DiscCatalogRepository
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
```

```kotlin
// FlightChartCanvas.kt
package com.inknironapps.bagger.ui.screens.comparison

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.inknironapps.bagger.data.db.entity.DiscEntity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Crude flight-shape projection.
 * Maps speed (forward distance), glide (drift), turn (initial right), fade (terminal left)
 * onto a 2D path drawn from launcher (bottom-center) up the canvas.
 */
@Composable
fun FlightChartCanvas(discs: List<Pair<DiscEntity, Color>>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        discs.forEach { (disc, color) ->
            val path = flightPath(disc, cx, h, w)
            drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
        }
    }
}

private fun flightPath(disc: DiscEntity, cx: Float, h: Float, w: Float): Path {
    val path = Path()
    path.moveTo(cx, h)
    val steps = 60
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        // Forward progress
        val y = h - (disc.speed / 14f) * h * t
        // Lateral: turn early (negative = right drift), fade late (positive = left drift = -x for RHBH)
        val turnPhase = sin(PI * t).toFloat() * disc.turn / 5f
        val fadePhase = ((t * t) * disc.fade / 6f) * -1
        val xOffset = (turnPhase + fadePhase) * (w * 0.3f)
        path.lineTo(cx + xOffset, y)
    }
    return path
}
```

```kotlin
// ComparisonScreen.kt
package com.inknironapps.bagger.ui.screens.comparison

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(onBack: () -> Unit, vm: ComparisonViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val palette = listOf(Color(0xFF095F73), Color(0xFFC53030), Color(0xFFD69E2E))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare discs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.picked.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Flight comparison", style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.fillMaxWidth().height(220.dp)) {
                            FlightChartCanvas(state.picked.mapIndexed { i, d -> d to palette[i % palette.size] })
                        }
                        state.picked.forEachIndexed { i, d ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(12.dp).background(palette[i % palette.size], shape = androidx.compose.foundation.shape.CircleShape))
                                Text("${d.brand} ${d.mold}")
                                FlightNumbersRow(d.speed, d.glide, d.turn, d.fade)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("Search to add (max 3)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )

            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.all, key = { it.id }) { d ->
                    val picked = state.picked.any { it.id == d.id }
                    Card(onClick = { vm.togglePick(d) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = picked, onCheckedChange = { vm.togglePick(d) })
                            Column {
                                Text("${d.brand} — ${d.mold}", style = MaterialTheme.typography.titleSmall)
                                FlightNumbersRow(d.speed, d.glide, d.turn, d.fade)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Need import `androidx.compose.foundation.background` for the indicator dot. Add it.

Commit: `feat: add disc comparison w/ flight-chart Canvas overlay`

---

## Task 7: More Tab Menu

Replace `MoreScreen.kt` with a real menu pointing at Wishlist, Lost Map, Comparison, Stats (placeholder), Settings (placeholder).

```kotlin
// MoreScreen.kt
package com.inknironapps.bagger.ui.screens.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onWishlist: () -> Unit,
    onLostMap: () -> Unit,
    onCompare: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("More") }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(8.dp)) {
            item { MenuItem("Wishlist", Icons.Filled.FavoriteBorder, onWishlist) }
            item { MenuItem("Lost discs map", Icons.Filled.LocationOn, onLostMap) }
            item { MenuItem("Compare discs", Icons.Filled.Compare, onCompare) }
            item { HorizontalDivider() }
            item { MenuItem("Stats", Icons.Filled.QueryStats, onStats) }
            item { MenuItem("Settings", Icons.Filled.Settings, onSettings) }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
```

Need imports:
```kotlin
import androidx.compose.foundation.clickable
```

Update `Destinations.kt` `DetailRoutes`:
```kotlin
const val Wishlist = "wishlist"
const val LostMap = "lost_map"
const val Compare = "compare"
const val Stats = "stats"        // placeholder for Plan 7
const val Settings = "settings"  // placeholder for Plan 7
```

Update `BaggerNavHost.kt`:
```kotlin
composable(Destination.More.route) {
    MoreScreen(
        onWishlist = { navController.navigate(DetailRoutes.Wishlist) },
        onLostMap  = { navController.navigate(DetailRoutes.LostMap) },
        onCompare  = { navController.navigate(DetailRoutes.Compare) },
        onStats    = { navController.navigate(DetailRoutes.Stats) },
        onSettings = { navController.navigate(DetailRoutes.Settings) }
    )
}
composable(DetailRoutes.Wishlist) {
    WishlistScreen(onBack = { navController.popBackStack() })
}
composable(DetailRoutes.LostMap) {
    LostMapScreen(onBack = { navController.popBackStack() })
}
composable(DetailRoutes.Compare) {
    ComparisonScreen(onBack = { navController.popBackStack() })
}
composable(DetailRoutes.Stats) {
    StatsPlaceholder(onBack = { navController.popBackStack() })
}
composable(DetailRoutes.Settings) {
    SettingsPlaceholder(onBack = { navController.popBackStack() })
}
```

Add tiny placeholders:

```kotlin
// ui/screens/StatsPlaceholder.kt
package com.inknironapps.bagger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inknironapps.bagger.ui.components.EmptyState

@Composable
fun StatsPlaceholder(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) { EmptyState("Stats", "Coming in Plan 7.") }
}

@Composable
fun SettingsPlaceholder(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) { EmptyState("Settings", "Coming in Plan 7.") }
}
```

Delete the old `ui/screens/MoreScreen.kt` from Plan 1 (now replaced by `ui/screens/more/MoreScreen.kt`).

Commit: `feat: rewrite More tab as menu w/ Wishlist + Lost Map + Compare entries`

---

## Task 8: Push + Verify CI + Tag

```markdown
## [0.6.0] - 2026-05-01

### Added

- Wishlist with one-tap add from any catalog disc detail page and a Bought it action that converts a wishlist item into an owned disc on your shelf.
- Lost disc tracking: marking a disc Lost from the disc detail screen now opens a dialog for course name, hole number, optional notes, and an opt-in GPS pin. The Lost discs map under More renders pinned events on Google Maps; events without GPS appear in a list below.
- Disc comparison screen for picking two or three discs side by side with a flight-chart Canvas overlay and flight numbers per disc.
- More tab is now a real menu wiring Wishlist, Lost discs map, Compare discs, plus placeholder rows for Stats and Settings (filled in by Plan 7).
- New repository layer for the wishlist and lost-disc-event tables; LocationProvider wraps FusedLocationProviderClient.
```

Push, verify CI, tag.

---

## Verification Checklist

- [ ] All 8 task commits land
- [ ] Build green
- [ ] Lint clean
- [ ] Unit tests still pass (no new ones for Plan 6 — UI/Compose)
- [ ] CI auto-merge + release green
- [ ] CHANGELOG v0.6.0 tagged

Note on Maps API: without a `MAPS_API_KEY` set in `local.properties` or env, the map renders a watermarked dev mode. Production deploy needs a real key — tracked for Plan 8.

---

## Out of Scope

- **Plan 7:** Stats + Settings (real screens replace placeholders)
- **Plan 8:** Release pipeline incl. MAPS_API_KEY in CI secrets

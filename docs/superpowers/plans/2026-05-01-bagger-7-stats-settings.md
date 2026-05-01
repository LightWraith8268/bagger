# Bagger Plan 7 — Stats + Settings + Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Stats screen w/ inventory totals + brand/type pies. Real Settings screen w/ theme, backup/restore JSON, CSV export, "delete all my data", in-app update card, what's new dialog, privacy/terms/feedback links. Onboarding empty-state on first launch. Polished permission flows.

**Architecture:** Stats reads via existing repos. Backup uses Storage Access Framework (`ActivityResultContracts.CreateDocument` / `OpenDocument`). CSV export same SAF. In-app update via Play Core `AppUpdateManager`. What's New parses `assets/CHANGELOG.md` against `BaggerPrefs.lastSeenChangelogVersion`.

**Tech:** `app-update-ktx 2.1.0` (Play Core). No other new deps.

---

## File Structure

```
app/src/main/java/com/inknironapps/bagger/
├── ui/screens/stats/
│   ├── StatsViewModel.kt
│   └── StatsScreen.kt
├── ui/screens/settings/
│   ├── SettingsViewModel.kt
│   ├── SettingsScreen.kt
│   ├── ThemeSection.kt
│   ├── BackupSection.kt
│   ├── DataSection.kt          # CSV export, delete all
│   ├── UpdateCard.kt           # in-app update
│   └── AboutSection.kt         # version footer + privacy/terms/feedback
├── ui/screens/onboarding/
│   └── OnboardingScreen.kt
├── ui/whatsnew/
│   └── WhatsNewDialog.kt
├── data/backup/
│   ├── BackupExporter.kt
│   ├── BackupImporter.kt
│   └── CsvExporter.kt
├── data/update/
│   └── UpdateChecker.kt
└── data/changelog/
    └── ChangelogParser.kt
app/src/main/assets/
└── CHANGELOG.md                # bundled, sym-copied at build time via gradle task
```

---

## Task 1: Deps + Setup

- [ ] Add to `gradle/libs.versions.toml`:
```toml
[versions]
play-app-update = "2.1.0"

[libraries]
play-app-update = { module = "com.google.android.play:app-update", version.ref = "play-app-update" }
play-app-update-ktx = { module = "com.google.android.play:app-update-ktx", version.ref = "play-app-update" }
```

- [ ] Add deps:
```kotlin
implementation(libs.play.app.update)
implementation(libs.play.app.update.ktx)
```

- [ ] Add Gradle task to copy `CHANGELOG.md` into `app/src/main/assets/` at build time:

In `app/build.gradle.kts` after `dependencies { }`:

```kotlin
val copyChangelog by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
afterEvaluate {
    tasks.matching { it.name.startsWith("preBuild") }.configureEach {
        dependsOn(copyChangelog)
    }
}
```

- [ ] Add `app/src/main/assets/CHANGELOG.md` to `.gitignore` since it's auto-generated.

- [ ] Build green.

Commit: `chore: add Play Core app-update + CHANGELOG copy task`

---

## Task 2: Stats Screen

```kotlin
// StatsViewModel.kt
package com.inknironapps.bagger.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.LostDiscEventRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.util.Calendar

data class StatsUi(
    val totalDiscs: Int = 0,
    val totalValue: Long = 0,           // cents
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
        val cal = Calendar.getInstance().apply { set(Calendar.MONTH, 0); set(Calendar.DAY_OF_MONTH, 1) }
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
```

```kotlin
// StatsScreen.kt
package com.inknironapps.bagger.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, vm: StatsViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Stats") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Total discs", s.totalDiscs.toString())
            if (s.totalValue > 0) {
                StatCard("Inventory value", "$" + (s.totalValue / 100.0).format(2))
            }
            StatCard("Lost this year", s.lostThisYear.toString())
            StatCard("Retired", s.retiredCount.toString())

            if (s.byBrand.isNotEmpty()) {
                BarSection("By brand", s.byBrand)
            }
            if (s.byType.isNotEmpty()) {
                BarSection("By type", s.byType)
            }
            if (s.byPlastic.isNotEmpty()) {
                BarSection("By plastic", s.byPlastic)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun BarSection(title: String, data: Map<String, Int>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val maxV = data.values.max()
            data.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(k, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.weight(1f).height(20.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width * (v.toFloat() / maxV.toFloat())
                            drawRect(color = Color(0xFF095F73), topLeft = Offset.Zero, size = Size(w, size.height))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(v.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
```

Commit: `feat: add Stats screen w/ totals + brand/type/plastic charts`

---

## Task 3: Backup Exporter / Importer + CSV

```kotlin
// data/backup/BackupExporter.kt
package com.inknironapps.bagger.data.backup

import com.inknironapps.bagger.data.db.dao.BagDao
import com.inknironapps.bagger.data.db.dao.LostDiscEventDao
import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import com.inknironapps.bagger.data.db.dao.WishlistDao
import com.inknironapps.bagger.data.db.entity.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BaggerBackup(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val ownedDiscs: List<OwnedDiscBackup>,
    val bags: List<BagBackup>,
    val lostEvents: List<LostBackup>,
    val wishlist: List<WishlistBackup>
)

@Serializable
data class OwnedDiscBackup(
    val id: String, val discId: String, val plasticType: String?, val weight: Int?,
    val color: String?, val condition: String, val state: String, val bagId: String?,
    val purchaseDate: Long?, val purchasePrice: Long?, val notes: String?,
    val isOriginalOwner: Boolean, val customTags: List<String>,
    val createdAt: Long, val updatedAt: Long
)

@Serializable
data class BagBackup(val id: String, val name: String, val description: String?, val iconColor: String,
    val sortOrder: Int, val createdAt: Long, val updatedAt: Long)

@Serializable
data class LostBackup(val id: String, val ownedDiscId: String, val lostAt: Long,
    val lat: Double?, val lng: Double?, val courseName: String?, val holeNumber: Int?,
    val notes: String?, val foundAt: Long?)

@Serializable
data class WishlistBackup(val id: String, val discId: String, val addedAt: Long,
    val targetWeight: Int?, val targetPlastic: String?, val notes: String?)

@Singleton
class BackupExporter @Inject constructor(
    private val ownedDao: OwnedDiscDao,
    private val bagDao: BagDao,
    private val lostDao: LostDiscEventDao,
    private val wishlistDao: WishlistDao
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun export(): String {
        val owned = ownedDao.getAll().map {
            OwnedDiscBackup(
                it.id, it.discId, it.plasticType, it.weight, it.color, it.condition, it.state,
                it.bagId, it.purchaseDate, it.purchasePrice, it.notes, it.isOriginalOwner,
                it.customTags, it.createdAt, it.updatedAt
            )
        }
        // bags + lost + wishlist via observing snapshots — but we need synchronous; add 1-shot helpers in DAOs
        val bags = bagDao.getAllOnce()
        val lost = lostDao.getAllOnce()
        val wishlist = wishlistDao.getAllOnce()
        val backup = BaggerBackup(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            ownedDiscs = owned,
            bags = bags.map { BagBackup(it.id, it.name, it.description, it.iconColor, it.sortOrder, it.createdAt, it.updatedAt) },
            lostEvents = lost.map { LostBackup(it.id, it.ownedDiscId, it.lostAt, it.lat, it.lng, it.courseName, it.holeNumber, it.notes, it.foundAt) },
            wishlist = wishlist.map { WishlistBackup(it.id, it.discId, it.addedAt, it.targetWeight, it.targetPlastic, it.notes) }
        )
        return json.encodeToString(BaggerBackup.serializer(), backup)
    }
}
```

Need to add `getAllOnce()` to existing DAOs:

```kotlin
// BagDao.kt — add
@Query("SELECT * FROM bags") suspend fun getAllOnce(): List<BagEntity>

// LostDiscEventDao.kt — add
@Query("SELECT * FROM lost_disc_events") suspend fun getAllOnce(): List<LostDiscEventEntity>

// WishlistDao.kt — add
@Query("SELECT * FROM wishlist_items") suspend fun getAllOnce(): List<WishlistItemEntity>
```

```kotlin
// data/backup/BackupImporter.kt
package com.inknironapps.bagger.data.backup

import com.inknironapps.bagger.data.db.dao.BagDao
import com.inknironapps.bagger.data.db.dao.LostDiscEventDao
import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import com.inknironapps.bagger.data.db.dao.WishlistDao
import com.inknironapps.bagger.data.db.entity.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporter @Inject constructor(
    private val ownedDao: OwnedDiscDao,
    private val bagDao: BagDao,
    private val lostDao: LostDiscEventDao,
    private val wishlistDao: WishlistDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed class Result {
        data class Success(val counts: Counts) : Result()
        data class Failure(val message: String) : Result()
    }
    data class Counts(val owned: Int, val bags: Int, val lost: Int, val wishlist: Int)

    suspend fun import(jsonText: String): Result = runCatching {
        val backup = json.decodeFromString(BaggerBackup.serializer(), jsonText)
        if (backup.schemaVersion > 1) return Result.Failure("Backup is from a newer app version. Please update Bagger.")

        backup.bags.forEach { b ->
            bagDao.upsert(BagEntity(b.id, b.name, b.description, b.iconColor, b.sortOrder, b.createdAt, b.updatedAt, null, null))
        }
        backup.ownedDiscs.forEach { o ->
            ownedDao.upsert(OwnedDiscEntity(
                o.id, o.discId, o.plasticType, o.weight, o.color, o.condition, o.state,
                o.bagId, o.purchaseDate, o.purchasePrice, o.notes, o.isOriginalOwner,
                o.customTags, o.createdAt, o.updatedAt, null, null
            ))
        }
        backup.lostEvents.forEach { l ->
            lostDao.upsert(LostDiscEventEntity(l.id, l.ownedDiscId, l.lostAt, l.lat, l.lng, l.courseName, l.holeNumber, l.notes, l.foundAt))
        }
        backup.wishlist.forEach { w ->
            wishlistDao.upsert(WishlistItemEntity(w.id, w.discId, w.addedAt, w.targetWeight, w.targetPlastic, w.notes))
        }
        Result.Success(Counts(backup.ownedDiscs.size, backup.bags.size, backup.lostEvents.size, backup.wishlist.size))
    }.getOrElse { Result.Failure(it.message ?: "Import failed") }
}
```

```kotlin
// data/backup/CsvExporter.kt
package com.inknironapps.bagger.data.backup

import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    private val ownedDao: OwnedDiscDao,
    private val discDao: DiscDao
) {
    suspend fun export(): String {
        val owned = ownedDao.getAll()
        val sb = StringBuilder()
        sb.appendLine("brand,mold,plastic,weight,color,condition,state,purchase_date,purchase_price_cents,notes")
        owned.forEach { o ->
            val d = discDao.getById(o.discId)
            sb.append(d?.brand?.csvEscape() ?: "")
            sb.append(',').append(d?.mold?.csvEscape() ?: "")
            sb.append(',').append(o.plasticType?.csvEscape() ?: "")
            sb.append(',').append(o.weight?.toString() ?: "")
            sb.append(',').append(o.color?.csvEscape() ?: "")
            sb.append(',').append(o.condition.csvEscape())
            sb.append(',').append(o.state.csvEscape())
            sb.append(',').append(o.purchaseDate?.toString() ?: "")
            sb.append(',').append(o.purchasePrice?.toString() ?: "")
            sb.append(',').append(o.notes?.csvEscape() ?: "")
            sb.appendLine()
        }
        return sb.toString()
    }
}

private fun String.csvEscape(): String =
    if (contains(',') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\""
    else this
```

Commit: `feat: add JSON backup + CSV export logic`

---

## Task 4: UpdateChecker + ChangelogParser + WhatsNewDialog

```kotlin
// data/update/UpdateChecker.kt
package com.inknironapps.bagger.data.update

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class UpdateState {
    data object Idle : UpdateState()
    data object UpToDate : UpdateState()
    data class UpdateAvailable(val versionCode: Int) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

@Singleton
class UpdateChecker @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager by lazy { AppUpdateManagerFactory.create(context) }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    suspend fun check() {
        try {
            val info = await(manager.appUpdateInfo)
            _state.value = when (info.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> UpdateState.UpdateAvailable(info.availableVersionCode())
                else -> UpdateState.UpToDate
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "check failed")
        }
    }

    private suspend fun await(task: com.google.android.gms.tasks.Task<AppUpdateInfo>): AppUpdateInfo =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.cancel(it) }
        }
}
```

```kotlin
// data/changelog/ChangelogParser.kt
package com.inknironapps.bagger.data.changelog

object ChangelogParser {

    data class Entry(val version: String, val sections: Map<String, List<String>>)

    fun parse(markdown: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val versionRegex = Regex("^##\\s+\\[?(\\d+\\.\\d+\\.\\d+)\\]?.*$", RegexOption.MULTILINE)
        val matches = versionRegex.findAll(markdown).toList()
        matches.forEachIndexed { idx, m ->
            val version = m.groupValues[1]
            val start = m.range.last + 1
            val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else markdown.length
            val body = markdown.substring(start, end)
            val sections = mutableMapOf<String, MutableList<String>>()
            var currentSection: String? = null
            body.lines().forEach { line ->
                val sectionMatch = Regex("^###\\s+(.+)$").matchEntire(line.trim())
                if (sectionMatch != null) {
                    currentSection = sectionMatch.groupValues[1].trim()
                    sections.getOrPut(currentSection!!) { mutableListOf() }
                } else if (currentSection != null && line.trim().startsWith("- ")) {
                    sections[currentSection]!!.add(line.trim().removePrefix("- "))
                }
            }
            entries.add(Entry(version, sections))
        }
        return entries
    }

    fun entriesBetween(parsed: List<Entry>, from: String?, to: String): List<Entry> {
        val toV = parseVersion(to)
        val fromV = from?.let { parseVersion(it) }
        return parsed.filter { e ->
            val v = parseVersion(e.version)
            v <= toV && (fromV == null || v > fromV)
        }
    }

    private fun parseVersion(s: String): IntArray =
        s.split(".").map { it.toIntOrNull() ?: 0 }.toIntArray()

    private operator fun IntArray.compareTo(other: IntArray): Int {
        val len = maxOf(size, other.size)
        for (i in 0 until len) {
            val a = if (i < size) this[i] else 0
            val b = if (i < other.size) other[i] else 0
            if (a != b) return a - b
        }
        return 0
    }
}
```

Test:

```kotlin
// app/src/test/.../ChangelogParserTest.kt
package com.inknironapps.bagger.data.changelog

import org.junit.Test
import kotlin.test.assertEquals

class ChangelogParserTest {
    private val sample = """
        # Changelog
        
        ## [Unreleased]
        
        ## [0.3.0] - 2026-05-01
        
        ### Added
        - Sync feature
        
        ## [0.2.0] - 2026-05-01
        
        ### Added
        - DB pipeline
        - Schema validation
        
        ### Changed
        - Test bumped
        
        ## [0.1.0] - 2026-04-30
        
        ### Added
        - Initial scaffold
    """.trimIndent()

    @Test fun parsesAllVersions() {
        val parsed = ChangelogParser.parse(sample)
        assertEquals(3, parsed.size)
        assertEquals("0.3.0", parsed[0].version)
    }

    @Test fun extractsSectionEntries() {
        val parsed = ChangelogParser.parse(sample)
        val v02 = parsed.first { it.version == "0.2.0" }
        assertEquals(2, v02.sections["Added"]?.size)
        assertEquals(1, v02.sections["Changed"]?.size)
    }

    @Test fun entriesBetweenIsExclusiveOnFromInclusiveOnTo() {
        val parsed = ChangelogParser.parse(sample)
        val between = ChangelogParser.entriesBetween(parsed, "0.1.0", "0.3.0")
        assertEquals(2, between.size)
        assertEquals(setOf("0.2.0", "0.3.0"), between.map { it.version }.toSet())
    }
}
```

```kotlin
// ui/whatsnew/WhatsNewDialog.kt
package com.inknironapps.bagger.ui.whatsnew

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inknironapps.bagger.data.changelog.ChangelogParser

@Composable
fun WhatsNewDialog(entries: List<ChangelogParser.Entry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                entries.forEach { entry ->
                    Text("v${entry.version}", style = MaterialTheme.typography.titleMedium)
                    entry.sections.forEach { (section, items) ->
                        Text(section, style = MaterialTheme.typography.titleSmall)
                        items.forEach { Text("• $it") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}
```

Commit: `feat: add UpdateChecker + ChangelogParser + WhatsNewDialog`

---

## Task 5: Settings Screen

```kotlin
// SettingsViewModel.kt
package com.inknironapps.bagger.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.BuildConfig
import com.inknironapps.bagger.data.backup.BackupExporter
import com.inknironapps.bagger.data.backup.BackupImporter
import com.inknironapps.bagger.data.backup.CsvExporter
import com.inknironapps.bagger.data.db.BaggerDatabase
import com.inknironapps.bagger.data.prefs.BaggerPrefs
import com.inknironapps.bagger.data.update.UpdateChecker
import com.inknironapps.bagger.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUi(
    val themeMode: String = "system",
    val versionName: String = BuildConfig.VERSION_NAME,
    val updateState: UpdateState = UpdateState.Idle,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: BaggerPrefs,
    private val backupExporter: BackupExporter,
    private val backupImporter: BackupImporter,
    private val csvExporter: CsvExporter,
    private val updateChecker: UpdateChecker,
    private val db: BaggerDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val ui: StateFlow<SettingsUi> = combine(prefs.themeMode, updateChecker.state) { theme, update ->
        SettingsUi(themeMode = theme, updateState = update)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUi())

    init { viewModelScope.launch { updateChecker.check() } }

    fun setTheme(mode: String) { viewModelScope.launch { prefs.setThemeMode(mode) } }

    suspend fun exportBackupJson(): String = backupExporter.export()
    suspend fun importBackupJson(text: String): String {
        return when (val r = backupImporter.import(text)) {
            is BackupImporter.Result.Success -> "Restored ${r.counts.owned} discs, ${r.counts.bags} bags."
            is BackupImporter.Result.Failure -> r.message
        }
    }
    suspend fun exportCsv(): String = csvExporter.export()

    fun deleteAllData() {
        viewModelScope.launch {
            db.clearAllTables()
            // Wipe DataStore prefs too
            // (rough — caller can show toast/restart prompt)
        }
    }
}
```

```kotlin
// SettingsScreen.kt
package com.inknironapps.bagger.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.data.update.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snackMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = vm.exportBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            snackMessage = "Backup exported"
        }
    }

    val backupOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@launch
            snackMessage = vm.importBackupJson(text)
        }
    }

    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = vm.exportCsv()
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            snackMessage = "CSV exported"
        }
    }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(snackMessage) { snackMessage?.let { snack.showSnackbar(it); snackMessage = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            if (state.updateState is UpdateState.UpdateAvailable) {
                Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Update available", style = MaterialTheme.typography.titleMedium)
                        Text("A newer version of Bagger is available on the Play Store.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                "market://details?id=${context.packageName}".toUri())
                            context.startActivity(intent)
                        }) { Text("Open Play Store") }
                    }
                }
            }

            SectionHeader("Appearance")
            Column(Modifier.padding(horizontal = 16.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, label) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.themeMode == k, onClick = { vm.setTheme(k) })
                        Text(label)
                    }
                }
            }

            SectionHeader("Backup & data")
            ListItem(
                headlineContent = { Text("Export backup (JSON)") },
                modifier = Modifier.clickable { backupSaver.launch("bagger-backup.json") }
            )
            ListItem(
                headlineContent = { Text("Restore from backup") },
                modifier = Modifier.clickable { backupOpener.launch(arrayOf("application/json")) }
            )
            ListItem(
                headlineContent = { Text("Export inventory CSV") },
                modifier = Modifier.clickable { csvSaver.launch("bagger-inventory.csv") }
            )
            ListItem(
                headlineContent = { Text("Delete all my data") },
                modifier = Modifier.clickable { showDeleteConfirm = true }
            )

            SectionHeader("About")
            ListItem(
                headlineContent = { Text("Privacy policy") },
                modifier = Modifier.clickable {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        "https://lightwraith8268.github.io/inknironapps-legal/privacy-policy.html".toUri()))
                }
            )
            ListItem(
                headlineContent = { Text("Terms & Conditions") },
                modifier = Modifier.clickable {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        "https://lightwraith8268.github.io/inknironapps-legal/terms.html".toUri()))
                }
            )
            ListItem(
                headlineContent = { Text("Send feedback") },
                modifier = Modifier.clickable {
                    val mail = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = "mailto:info@inknironapps.com".toUri()
                        putExtra(android.content.Intent.EXTRA_SUBJECT,
                            "Bagger feedback (v${state.versionName})")
                    }
                    context.startActivity(mail)
                }
            )

            ListItem(
                headlineContent = { Text("Bagger v${state.versionName}", style = MaterialTheme.typography.bodySmall) },
                supportingContent = { Text("Ink & Iron Apps", style = MaterialTheme.typography.labelSmall) }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete all data?") },
                text = { Text("This wipes every disc, bag, lost event, and wishlist item from this device. Cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteAllData()
                        showDeleteConfirm = false
                        snackMessage = "All data deleted"
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
}
```

Add `import androidx.compose.foundation.clickable`. Add `import androidx.compose.foundation.layout.HorizontalDivider` if used.

Commit: `feat: add Settings screen w/ theme + backup/restore + CSV + about`

---

## Task 6: Onboarding + WhatsNewDialog Hook

```kotlin
// ui/screens/onboarding/OnboardingScreen.kt
package com.inknironapps.bagger.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.PhotoCamera, null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Bagger", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("Snap a photo of any disc and Bagger catalogs it with flight numbers, plastic, and weight. Track multiple bags, mark discs lost, and build a wishlist.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth()) {
            Text("Snap your first disc")
        }
    }
}
```

Wire onboarding in MainActivity — show until `prefs.onboardingComplete` is true. After Settings introduces a way to mark, also auto-mark on first add disc. Update `BaggerApp.kt` or just check in `MainActivity` content composable:

```kotlin
// In MainActivity.kt, replace the BaggerTheme content with:
val onboarded by prefs.onboardingComplete.collectAsState(initial = true)
if (!onboarded) {
    OnboardingScreen(onGetStarted = {
        scope.launch { prefs.setOnboardingComplete(true) }
        nav.navigate(DetailRoutes.AddDisc)
    })
} else {
    Scaffold(bottomBar = { BaggerBottomBar(nav) }) { padding -> /* existing nav host */ }
}
```

Also wire WhatsNewDialog. In MainActivity (or a wrapper):

```kotlin
val lastSeen by prefs.lastSeenChangelogVersion.collectAsState(initial = null)
var entries by remember { mutableStateOf<List<ChangelogParser.Entry>>(emptyList()) }
LaunchedEffect(Unit) {
    if (lastSeen != BuildConfig.VERSION_NAME) {
        val text = context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        val parsed = ChangelogParser.parse(text)
        val between = ChangelogParser.entriesBetween(parsed, lastSeen, BuildConfig.VERSION_NAME)
        if (between.isNotEmpty() && lastSeen != null) {
            entries = between
        }
        prefs.setLastSeenChangelogVersion(BuildConfig.VERSION_NAME)
    }
}
if (entries.isNotEmpty()) {
    WhatsNewDialog(entries, onDismiss = { entries = emptyList() })
}
```

(The `lastSeen != null` check ensures fresh-install users don't see the dialog — onboarding covers them.)

Commit: `feat: add onboarding empty-state + What's New dialog wiring`

---

## Task 7: Wire Settings + Stats Routes

Update `BaggerNavHost.kt` `Stats` and `Settings` placeholders to real screens:

```kotlin
composable(DetailRoutes.Stats) {
    StatsScreen(onBack = { navController.popBackStack() })
}
composable(DetailRoutes.Settings) {
    SettingsScreen(onBack = { navController.popBackStack() })
}
```

Delete `ui/screens/StatsPlaceholder.kt` and `SettingsPlaceholder.kt` from Plan 6.

Commit: `feat: wire real Stats + Settings screens into nav`

---

## Task 8: Push + Verify CI + Tag

```markdown
## [0.7.0] - 2026-05-01

### Added

- Stats screen showing total disc count, lost-this-year, retired count, and bar charts grouped by brand, disc type, and plastic.
- Real Settings screen replacing the prior placeholder. Sections cover appearance (system/light/dark theme switcher), backup & data (JSON export and import via Storage Access Framework, CSV inventory export, delete all data), about (privacy policy, terms, send feedback, version footer).
- In-app update card backed by Play Core's AppUpdateManager. When a Play Store update is available, an opt-in card appears at the top of Settings linking to the listing.
- What's New dialog. After updating, a parsed-from-`CHANGELOG.md` summary covering every release between the previously-installed version and the current build is shown once, then dismissed.
- Onboarding empty-state on first launch with a Snap your first disc call to action.
- Unit tests for the changelog parser covering version extraction, section parsing, and inclusive/exclusive `entriesBetween` ranges.
```

Push, verify CI, tag.

---

## Verification

- [ ] All 8 commits land
- [ ] Build green
- [ ] Lint clean
- [ ] Unit tests: 19 prior + 3 ChangelogParser = 22 passing
- [ ] CI auto-merge + release green
- [ ] CHANGELOG v0.7.0 tagged

---

## Out of Scope

- **Plan 8:** Release pipeline (signing, AAB, Play Store, MAPS_API_KEY in CI secrets)
- Privacy policy hosting (assumed external — user's existing GitHub Pages site)

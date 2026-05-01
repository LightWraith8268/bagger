# Bagger Plan 5 — Photo-ID Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Add camera-based disc identification flow. CameraX captures → ML Kit Text Recognition extracts text → JaroWinkler matcher scores against `Disc` catalog → routes to confirm/pick/fallback. Manual add flow's "details form" step lives next to the confirm step (used by both photo-confirmed and manual paths). Photo storage in app-private dir, queue training data on manual correction.

**Architecture:** New module `ml/` for matcher logic (pure Kotlin, JVM-testable). New `data/photo/` for image storage. New screen flow under `ui/screens/add_disc/` w/ 5 steps: Camera → Confirm | Pick | Fallback (Manual Search) → Details Form → Save. ViewModel coordinates the steps via shared state.

**Tech:** CameraX 1.4, ML Kit Text Recognition v2 (`com.google.mlkit:text-recognition:16.0.1`), Coil 2.7 (Compose).

---

## File Structure

```
app/src/main/java/com/inknironapps/bagger/
├── ml/
│   ├── JaroWinkler.kt              # pure algorithm, no Android deps
│   ├── TokenExtractor.kt           # OCR text → uppercase tokens
│   ├── DiscMatcher.kt              # tokens × catalog → ranked candidates
│   └── MatchResult.kt              # sealed class: Confident, Candidates, Fallback
├── data/
│   ├── photo/
│   │   └── PhotoStorage.kt         # save/load images in app-private dir
│   └── repo/
│       └── IdSubmissionRepository.kt # for IdSubmissionQueue entity
├── ui/screens/add_disc/
│   ├── AddDiscViewModel.kt         # coordinates all steps
│   ├── AddDiscRoute.kt             # nav entry point + sub-state graph
│   ├── CameraStep.kt               # CameraX capture
│   ├── ConfirmStep.kt              # confident match — "Is this it?"
│   ├── PickStep.kt                 # 5-candidate pick screen
│   ├── ManualSearchStep.kt         # search catalog, prefilled w/ tokens
│   ├── DetailsFormStep.kt          # plastic/weight/color/condition/notes
│   └── SaveAction.kt               # composable wrapper for finalize
└── BaggerApp.kt                    # add IdSubmissionQueue inject if needed (no — DAO direct)
```

---

## Task 1: Deps + Permissions

- [ ] Add to `gradle/libs.versions.toml` `[versions]`:
```toml
camerax = "1.4.1"
mlkit-text-recognition = "16.0.1"
coil = "2.7.0"
accompanist-permissions = "0.36.0"
```

- [ ] Add to `[libraries]`:
```toml
camerax-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camerax-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camerax-view = { module = "androidx.camera:camera-view", version.ref = "camerax" }
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkit-text-recognition" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist-permissions" }
```

- [ ] Add deps to `app/build.gradle.kts`:
```kotlin
implementation(libs.camerax.core)
implementation(libs.camerax.camera2)
implementation(libs.camerax.lifecycle)
implementation(libs.camerax.view)
implementation(libs.mlkit.text.recognition)
implementation(libs.coil.compose)
implementation(libs.accompanist.permissions)
```

- [ ] Add to `AndroidManifest.xml` (above `<application>`):
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

- [ ] Build green.

Commit: `chore: add CameraX + ML Kit text recognition + Coil + permissions deps`

---

## Task 2: JaroWinkler + TokenExtractor (Pure Kotlin, Unit-Testable)

Files:
- `ml/JaroWinkler.kt`
- `ml/TokenExtractor.kt`
- Tests: `app/src/test/java/com/inknironapps/bagger/ml/JaroWinklerTest.kt`, `TokenExtractorTest.kt`

```kotlin
// ml/JaroWinkler.kt
package com.inknironapps.bagger.ml

import kotlin.math.max
import kotlin.math.min

object JaroWinkler {
    private const val PREFIX_SCALE = 0.1
    private const val PREFIX_LEN = 4

    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val s1 = a.uppercase()
        val s2 = b.uppercase()
        val matchDistance = max(s1.length, s2.length) / 2 - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)
        var matches = 0
        for (i in s1.indices) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0
        var t = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) t++
            k++
        }
        val transpositions = t / 2.0
        val m = matches.toDouble()
        val jaro = (m / s1.length + m / s2.length + (m - transpositions) / m) / 3.0
        var prefix = 0
        val prefixCap = min(PREFIX_LEN, min(s1.length, s2.length))
        for (i in 0 until prefixCap) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return jaro + prefix * PREFIX_SCALE * (1 - jaro)
    }
}
```

```kotlin
// ml/TokenExtractor.kt
package com.inknironapps.bagger.ml

object TokenExtractor {
    private val NOISE = setOf("LLC", "INC", "CO", "GMBH", "DISCS", "GOLF", "DISC", "TM", "R")
    private val NUMERIC = Regex("^[0-9]+\\.?[0-9]*$")
    private val SYMBOLS = Regex("[\\u00AE\\u2122\\u00A9]")

    fun extract(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        return rawText
            .replace(SYMBOLS, " ")
            .split(Regex("[\\s\\n\\r\\t,.;:!?()\\[\\]{}/\\\\<>\"']+"))
            .map { it.uppercase().trim() }
            .filter { it.length >= 3 }
            .filter { !NUMERIC.matches(it) }
            .filter { it !in NOISE }
            .distinct()
    }
}
```

Tests:

```kotlin
// JaroWinklerTest.kt
package com.inknironapps.bagger.ml

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JaroWinklerTest {
    @Test fun identicalStrings() {
        assertEquals(1.0, JaroWinkler.similarity("DESTROYER", "DESTROYER"), 0.001)
    }

    @Test fun ocrTypoMatchesHigh() {
        // "Destrover" → OCR error from Destroyer: should still score very high
        assertTrue(JaroWinkler.similarity("DESTROVER", "DESTROYER") >= 0.90)
    }

    @Test fun unrelatedStringsScoreLow() {
        assertTrue(JaroWinkler.similarity("DESTROYER", "AVIAR") < 0.6)
    }

    @Test fun emptyStringIsZero() {
        assertEquals(0.0, JaroWinkler.similarity("", "AVIAR"), 0.001)
        assertEquals(0.0, JaroWinkler.similarity("AVIAR", ""), 0.001)
    }

    @Test fun prefixMatchBonus() {
        // Common prefix should boost score
        val withPrefix = JaroWinkler.similarity("BUZZZ", "BUZZ")
        val withoutPrefix = JaroWinkler.similarity("XYBUZ", "BUZZ")
        assertTrue(withPrefix > withoutPrefix)
    }
}
```

```kotlin
// TokenExtractorTest.kt
package com.inknironapps.bagger.ml

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenExtractorTest {
    @Test fun extractsBrandAndMold() {
        val text = "INNOVA\nChampion Destroyer\n175g"
        val tokens = TokenExtractor.extract(text)
        assertTrue("INNOVA" in tokens)
        assertTrue("CHAMPION" in tokens)
        assertTrue("DESTROYER" in tokens)
    }

    @Test fun stripsNoiseWords() {
        val tokens = TokenExtractor.extract("Innova Discs LLC")
        assertTrue("INNOVA" in tokens)
        assertEquals(false, "DISCS" in tokens)
        assertEquals(false, "LLC" in tokens)
    }

    @Test fun stripsTrademarkSymbols() {
        val tokens = TokenExtractor.extract("Aviar® TM ©")
        assertTrue("AVIAR" in tokens)
    }

    @Test fun filtersShortTokens() {
        val tokens = TokenExtractor.extract("X is OK fine")
        assertEquals(false, "X" in tokens)
        assertEquals(false, "IS" in tokens)
        assertEquals(false, "OK" in tokens)
        assertTrue("FINE" in tokens)
    }

    @Test fun filtersNumerics() {
        val tokens = TokenExtractor.extract("Buzzz 175 4 5 -1 1")
        assertTrue("BUZZZ" in tokens)
        assertEquals(false, "175" in tokens)
    }
}
```

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests "com.inknironapps.bagger.ml.*"` → 9 tests pass.

Commit: `feat: add JaroWinkler + token extractor for disc OCR matching`

---

## Task 3: DiscMatcher

Files:
- `ml/MatchResult.kt`
- `ml/DiscMatcher.kt`
- Test: `app/src/test/java/com/inknironapps/bagger/ml/DiscMatcherTest.kt`

```kotlin
// ml/MatchResult.kt
package com.inknironapps.bagger.ml

import com.inknironapps.bagger.data.db.entity.DiscEntity

sealed class MatchResult {
    data class Confident(val disc: DiscEntity, val score: Double) : MatchResult()
    data class Candidates(val candidates: List<ScoredDisc>) : MatchResult()
    data class Fallback(val tokens: List<String>) : MatchResult()
}

data class ScoredDisc(val disc: DiscEntity, val score: Double)
```

```kotlin
// ml/DiscMatcher.kt
package com.inknironapps.bagger.ml

import com.inknironapps.bagger.data.db.entity.DiscEntity

class DiscMatcher {

    companion object {
        const val CONFIDENT_THRESHOLD = 0.85
        const val CONFIDENT_GAP = 0.15
        const val CANDIDATE_THRESHOLD = 0.6
        const val MAX_CANDIDATES = 5
        private const val BRAND_WEIGHT = 0.4
        private const val MOLD_WEIGHT = 0.6
    }

    fun match(tokens: List<String>, catalog: List<DiscEntity>): MatchResult {
        if (tokens.isEmpty() || catalog.isEmpty()) return MatchResult.Fallback(tokens)

        val scored = catalog.map { disc -> ScoredDisc(disc, score(tokens, disc)) }
            .sortedByDescending { it.score }

        val top = scored.firstOrNull() ?: return MatchResult.Fallback(tokens)
        val second = scored.getOrNull(1)

        return when {
            top.score >= CONFIDENT_THRESHOLD &&
                (second == null || top.score - second.score >= CONFIDENT_GAP) ->
                MatchResult.Confident(top.disc, top.score)

            top.score >= CANDIDATE_THRESHOLD ->
                MatchResult.Candidates(scored.take(MAX_CANDIDATES).filter { it.score >= CANDIDATE_THRESHOLD })

            else -> MatchResult.Fallback(tokens)
        }
    }

    private fun score(tokens: List<String>, disc: DiscEntity): Double {
        val brandScore = bestMatch(tokens, disc.brand)
        val moldScore = bestMatch(tokens, disc.mold)
        return BRAND_WEIGHT * brandScore + MOLD_WEIGHT * moldScore
    }

    private fun bestMatch(tokens: List<String>, target: String): Double {
        if (target.isEmpty()) return 0.0
        return tokens.maxOfOrNull { JaroWinkler.similarity(it, target) } ?: 0.0
    }
}
```

Test:

```kotlin
// DiscMatcherTest.kt
package com.inknironapps.bagger.ml

import com.inknironapps.bagger.data.db.entity.DiscEntity
import org.junit.Test
import kotlin.test.assertTrue

class DiscMatcherTest {

    private fun disc(id: String, brand: String, mold: String) = DiscEntity(
        id = id, brand = brand, mold = mold,
        speed = 12f, glide = 5f, turn = -1f, fade = 3f,
        discType = "Driver", stability = "overstable",
        pdgaApproved = true, yearReleased = 2008, primaryStampUrl = null
    )

    private val catalog = listOf(
        disc("innova-destroyer", "Innova", "Destroyer"),
        disc("innova-aviar", "Innova", "Aviar"),
        disc("discraft-buzzz", "Discraft", "Buzzz"),
        disc("mvp-tesla", "MVP", "Tesla")
    )

    @Test fun confidentMatchOnExactTokens() {
        val result = DiscMatcher().match(listOf("INNOVA", "DESTROYER"), catalog)
        assertTrue(result is MatchResult.Confident)
        assertTrue((result as MatchResult.Confident).disc.id == "innova-destroyer")
        assertTrue(result.score >= 0.85)
    }

    @Test fun candidatesWhenAmbiguous() {
        // single token "DEST" partial — should produce candidates not confident
        val result = DiscMatcher().match(listOf("DEST"), catalog)
        assertTrue(result is MatchResult.Candidates || result is MatchResult.Fallback)
    }

    @Test fun fallbackWhenNoTokens() {
        val result = DiscMatcher().match(emptyList(), catalog)
        assertTrue(result is MatchResult.Fallback)
    }

    @Test fun fallbackWhenAllScoresBelowThreshold() {
        val result = DiscMatcher().match(listOf("ZZZZZ", "QQQQQ"), catalog)
        assertTrue(result is MatchResult.Fallback)
    }
}
```

Run + verify pass.

Commit: `feat: add DiscMatcher w/ confident/candidates/fallback decision`

---

## Task 4: PhotoStorage

Files:
- `data/photo/PhotoStorage.kt`

```kotlin
package com.inknironapps.bagger.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File = File(baseDir, "${UUID.randomUUID()}.jpg")

    fun savePhoto(bitmap: Bitmap, quality: Int = 85): File {
        val file = newPhotoFile()
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
        return file
    }

    fun loadBitmap(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun delete(path: String) { File(path).takeIf { it.exists() }?.delete() }
}
```

Add FileProvider to manifest (inside `<application>`):

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

Add `app/src/main/res/xml/file_provider_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="photos" path="photos/" />
    <cache-path name="updates" path="." />
</paths>
```

Commit: `feat: add PhotoStorage + FileProvider for app-private images`

---

## Task 5: AddDisc Flow — Camera + ML + Decision

Single big task: ViewModel + nav + 5 step screens. Implement together since they share state.

Files:
- `ui/screens/add_disc/AddDiscViewModel.kt`
- `ui/screens/add_disc/AddDiscRoute.kt`
- `ui/screens/add_disc/CameraStep.kt`
- `ui/screens/add_disc/ConfirmStep.kt`
- `ui/screens/add_disc/PickStep.kt`
- `ui/screens/add_disc/ManualSearchStep.kt`
- `ui/screens/add_disc/DetailsFormStep.kt`

```kotlin
// AddDiscViewModel.kt
package com.inknironapps.bagger.ui.screens.add_disc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.dao.IdSubmissionQueueDao
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.IdSubmissionQueueEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.photo.PhotoStorage
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import com.inknironapps.bagger.ml.DiscMatcher
import com.inknironapps.bagger.ml.MatchResult
import com.inknironapps.bagger.ml.ScoredDisc
import com.inknironapps.bagger.ml.TokenExtractor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

enum class AddStep { Camera, Confirm, Pick, ManualSearch, DetailsForm, Saved }

data class AddDiscState(
    val step: AddStep = AddStep.Camera,
    val photoPath: String? = null,
    val tokens: List<String> = emptyList(),
    val candidates: List<ScoredDisc> = emptyList(),
    val confidentMatch: DiscEntity? = null,
    val selectedDisc: DiscEntity? = null,
    val plasticType: String = "",
    val weight: String = "",
    val color: String = "",
    val condition: String = "Good",
    val notes: String = "",
    val isOriginalOwner: Boolean = true,
    val processing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddDiscViewModel @Inject constructor(
    private val photoStorage: PhotoStorage,
    private val catalogRepo: DiscCatalogRepository,
    private val ownedRepo: OwnedDiscRepository,
    private val idQueueDao: IdSubmissionQueueDao
) : ViewModel() {

    private val _state = MutableStateFlow(AddDiscState())
    val state: StateFlow<AddDiscState> = _state.asStateFlow()

    private val matcher = DiscMatcher()

    fun onPhotoCaptured(file: File) {
        _state.value = _state.value.copy(photoPath = file.absolutePath, processing = true)
        viewModelScope.launch {
            try {
                val image = InputImage.fromFilePath(applicationContextOrNull()!!, android.net.Uri.fromFile(file))
                val text = recognizeText(image)
                val tokens = TokenExtractor.extract(text)
                val catalog = catalogRepo.observeAll().first()
                val result = matcher.match(tokens, catalog)
                handleMatch(tokens, result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(processing = false, error = "ID failed: ${e.message}", step = AddStep.ManualSearch)
            }
        }
    }

    /** ML Kit recognizer needs Application context — caller wires via setter or DI; quick hack via inject of @ApplicationContext is cleaner. Use file-based InputImage variant w/o context: */
    fun onPhotoCapturedNoContext(file: File) {
        _state.value = _state.value.copy(photoPath = file.absolutePath, processing = true)
        viewModelScope.launch {
            try {
                val bitmap = photoStorage.loadBitmap(file.absolutePath)
                    ?: throw IllegalStateException("decode failed")
                val image = InputImage.fromBitmap(bitmap, 0)
                val text = recognizeText(image)
                val tokens = TokenExtractor.extract(text)
                val catalog = catalogRepo.observeAll().first()
                val result = matcher.match(tokens, catalog)
                handleMatch(tokens, result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(processing = false, error = e.message, step = AddStep.ManualSearch)
            }
        }
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }

    private fun handleMatch(tokens: List<String>, result: MatchResult) {
        _state.value = when (result) {
            is MatchResult.Confident -> _state.value.copy(
                step = AddStep.Confirm,
                tokens = tokens,
                confidentMatch = result.disc,
                selectedDisc = result.disc,
                processing = false
            )
            is MatchResult.Candidates -> _state.value.copy(
                step = AddStep.Pick,
                tokens = tokens,
                candidates = result.candidates,
                processing = false
            )
            is MatchResult.Fallback -> _state.value.copy(
                step = AddStep.ManualSearch,
                tokens = tokens,
                processing = false
            )
        }
    }

    fun confirmMatch() { _state.value = _state.value.copy(step = AddStep.DetailsForm) }
    fun rejectMatch() { _state.value = _state.value.copy(step = AddStep.ManualSearch, confidentMatch = null) }
    fun pickCandidate(disc: DiscEntity) { _state.value = _state.value.copy(selectedDisc = disc, step = AddStep.DetailsForm) }
    fun selectFromManualSearch(disc: DiscEntity) {
        _state.value = _state.value.copy(selectedDisc = disc, step = AddStep.DetailsForm)
        // Queue for training data if user corrected an OCR fallback
        val tokens = _state.value.tokens
        val photo = _state.value.photoPath
        if (tokens.isNotEmpty() && photo != null) {
            viewModelScope.launch {
                idQueueDao.upsert(IdSubmissionQueueEntity(
                    id = UUID.randomUUID().toString(),
                    photoPath = photo,
                    confirmedDiscId = disc.id,
                    ocrTokens = tokens,
                    capturedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    fun setPlastic(s: String) { _state.value = _state.value.copy(plasticType = s) }
    fun setWeight(s: String) { _state.value = _state.value.copy(weight = s) }
    fun setColor(s: String) { _state.value = _state.value.copy(color = s) }
    fun setCondition(s: String) { _state.value = _state.value.copy(condition = s) }
    fun setNotes(s: String) { _state.value = _state.value.copy(notes = s) }

    fun save() {
        val disc = _state.value.selectedDisc ?: return
        val s = _state.value
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            ownedRepo.upsert(OwnedDiscEntity(
                id = UUID.randomUUID().toString(),
                discId = disc.id,
                plasticType = s.plasticType.takeIf { it.isNotBlank() },
                weight = s.weight.toIntOrNull(),
                color = s.color.takeIf { it.isNotBlank() },
                condition = s.condition,
                state = "Shelf",
                bagId = null,
                purchaseDate = null,
                purchasePrice = null,
                notes = s.notes.takeIf { it.isNotBlank() },
                isOriginalOwner = s.isOriginalOwner,
                customTags = emptyList(),
                createdAt = now, updatedAt = now,
                userId = null, syncedAt = null
            ))
            _state.value = _state.value.copy(step = AddStep.Saved)
        }
    }

    fun reset() { _state.value = AddDiscState() }

    private fun applicationContextOrNull(): android.content.Context? = null
}
```

NOTE: The `applicationContextOrNull` shim above is ugly. Cleaner approach: inject `@ApplicationContext context` into the VM. The actual implementation should use `onPhotoCapturedNoContext` (uses Bitmap directly via PhotoStorage) — drop the unused `onPhotoCaptured(file)` variant.

The VM also needs `IdSubmissionQueueDao` provided via Hilt — since Plan 1 didn't add it, add to `DatabaseModule.kt`:

```kotlin
@Provides fun provideIdSubmissionQueueDao(db: BaggerDatabase): IdSubmissionQueueDao = db.idSubmissionQueueDao()
```

And add the abstract DAO accessor to `BaggerDatabase`:

```kotlin
abstract fun idSubmissionQueueDao(): IdSubmissionQueueDao
```

And create the missing DAO `app/src/main/java/com/inknironapps/bagger/data/db/dao/IdSubmissionQueueDao.kt`:

```kotlin
package com.inknironapps.bagger.data.db.dao

import androidx.room.*
import com.inknironapps.bagger.data.db.entity.IdSubmissionQueueEntity

@Dao
interface IdSubmissionQueueDao {
    @Upsert suspend fun upsert(entry: IdSubmissionQueueEntity)
    @Query("SELECT * FROM id_submission_queue ORDER BY capturedAt DESC") suspend fun getAll(): List<IdSubmissionQueueEntity>
    @Delete suspend fun delete(entry: IdSubmissionQueueEntity)
}
```

```kotlin
// AddDiscRoute.kt
package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AddDiscRoute(onDone: () -> Unit, vm: AddDiscViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    when (state.step) {
        AddStep.Camera -> CameraStep(onPhoto = vm::onPhotoCapturedNoContext, processing = state.processing)
        AddStep.Confirm -> ConfirmStep(
            disc = state.confidentMatch!!,
            onAccept = vm::confirmMatch,
            onReject = vm::rejectMatch
        )
        AddStep.Pick -> PickStep(
            candidates = state.candidates,
            onPick = vm::pickCandidate,
            onSearch = { vm.rejectMatch() }
        )
        AddStep.ManualSearch -> ManualSearchStep(
            initialQuery = state.tokens.joinToString(" "),
            onPick = vm::selectFromManualSearch
        )
        AddStep.DetailsForm -> DetailsFormStep(
            state = state,
            onPlastic = vm::setPlastic,
            onWeight = vm::setWeight,
            onColor = vm::setColor,
            onCondition = vm::setCondition,
            onNotes = vm::setNotes,
            onSave = vm::save
        )
        AddStep.Saved -> {
            androidx.compose.runtime.LaunchedEffect(Unit) { vm.reset(); onDone() }
        }
    }
}
```

```kotlin
// CameraStep.kt
package com.inknironapps.bagger.ui.screens.add_disc

import android.Manifest
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.EntryPointAccessors
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraStep(onPhoto: (File) -> Unit, processing: Boolean) {
    val perm = rememberPermissionState(Manifest.permission.CAMERA)
    when {
        perm.status.isGranted -> CameraView(onPhoto = onPhoto, processing = processing)
        else -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission needed", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("To identify a disc by photo, Bagger needs to use your camera.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { perm.launchPermissionRequest() }) { Text("Grant permission") }
        }
    }
}

@Composable
private fun CameraView(onPhoto: (File) -> Unit, processing: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val photoStorage = remember {
        // Use Hilt EntryPoint to retrieve PhotoStorage
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.inknironapps.bagger.di.PhotoStorageEntryPoint::class.java
        ).photoStorage()
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = androidx.camera.core.Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }
                        val capture = ImageCapture.Builder().build()
                        imageCapture = capture
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        FilledIconButton(
            onClick = {
                val capture = imageCapture ?: return@FilledIconButton
                val file = photoStorage.newPhotoFile()
                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(output, ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) { onPhoto(file) }
                        override fun onError(exception: ImageCaptureException) {}
                    })
            },
            enabled = !processing,
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(72.dp)
        ) {
            Icon(Icons.Filled.Camera, "Capture", modifier = Modifier.size(36.dp))
        }

        if (processing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
```

Need a Hilt `EntryPoint` so the camera composable can pull PhotoStorage:

```kotlin
// di/PhotoStorageEntryPoint.kt
package com.inknironapps.bagger.di

import com.inknironapps.bagger.data.photo.PhotoStorage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhotoStorageEntryPoint {
    fun photoStorage(): PhotoStorage
}
```

```kotlin
// ConfirmStep.kt
package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@Composable
fun ConfirmStep(disc: DiscEntity, onAccept: () -> Unit, onReject: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Is this it?", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(disc.brand, style = MaterialTheme.typography.titleSmall)
                Text(disc.mold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlightNumbersRow(disc.speed, disc.glide, disc.turn, disc.fade)
                Spacer(Modifier.height(4.dp))
                Text("${disc.discType} · ${disc.stability}")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReject) { Text("No, search") }
            Button(onClick = onAccept) { Text("Yes, that's it") }
        }
    }
}
```

```kotlin
// PickStep.kt
package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.ml.ScoredDisc
import com.inknironapps.bagger.ui.components.CatalogDiscCard

@Composable
fun PickStep(candidates: List<ScoredDisc>, onPick: (DiscEntity) -> Unit, onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Pick the closest match", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(candidates, key = { it.disc.id }) { sd ->
                CatalogDiscCard(sd.disc, { onPick(sd.disc) })
            }
        }
        OutlinedButton(onClick = onSearch, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(16.dp)) {
            Text("None of these — search manually")
        }
    }
}
```

```kotlin
// ManualSearchStep.kt
package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.ui.components.CatalogDiscCard
import com.inknironapps.bagger.ui.screens.discover.DiscoverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSearchStep(initialQuery: String, onPick: (DiscEntity) -> Unit, vm: DiscoverViewModel = hiltViewModel()) {
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) vm.setQuery(initialQuery) }
    val state by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search the catalog", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = { Text("Brand or mold") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.results, key = { it.id }) { d ->
                CatalogDiscCard(d, { onPick(d) })
            }
        }
    }
}
```

```kotlin
// DetailsFormStep.kt
package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsFormStep(
    state: AddDiscState,
    onPlastic: (String) -> Unit,
    onWeight: (String) -> Unit,
    onColor: (String) -> Unit,
    onCondition: (String) -> Unit,
    onNotes: (String) -> Unit,
    onSave: () -> Unit
) {
    val disc = state.selectedDisc ?: return
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(disc.brand, style = MaterialTheme.typography.titleSmall)
        Text(disc.mold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        FlightNumbersRow(disc.speed, disc.glide, disc.turn, disc.fade)
        Spacer(Modifier.height(8.dp))
        Text("Optional details", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(state.plasticType, onPlastic, label = { Text("Plastic (e.g. Star, ESP)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(state.weight, onWeight, label = { Text("Weight (g)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        OutlinedTextField(state.color, onColor, label = { Text("Color") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Text("Condition")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("New", "Good", "Beat", "Dyed").forEach { c ->
                FilterChip(selected = state.condition == c, onClick = { onCondition(c) }, label = { Text(c) })
            }
        }

        OutlinedTextField(state.notes, onNotes, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save to shelf") }
    }
}
```

Commit: `feat: add photo-ID flow w/ camera + ML Kit + matcher + details form`

---

## Task 6: Wire AddDisc into nav

Update `Destinations.kt` to add `AddDisc` non-tab route:

```kotlin
object DetailRoutes {
    // ... existing
    const val AddDisc = "add_disc"
}
```

Update `BaggerNavHost.kt`:

```kotlin
composable(DetailRoutes.AddDisc) {
    AddDiscRoute(onDone = { navController.popBackStack() })
}
```

Update `ShelfScreen.kt`:

```kotlin
ShelfScreen(
    onAddDisc = { navController.navigate(DetailRoutes.AddDisc) },
    onDiscClick = { id -> navController.navigate(DetailRoutes.ownedDetail(id)) }
)
```

(ShelfScreen `onAddDisc` was originally pointing to "discover" — change to AddDisc route.)

Commit: `feat: wire AddDisc flow into shelf FAB`

---

## Task 7: Push + Verify CI + Tag

```markdown
## [0.5.0] - 2026-05-01

### Added

- Photo-based disc identification flow. Tap Add disc to open the camera, snap a photo of the disc, and Bagger uses on-device ML Kit text recognition with a Jaro–Winkler matcher to identify the disc against its catalog. Confident matches go straight to a confirmation screen; ambiguous matches present up to five candidates; unrecognized photos fall back to a manual catalog search prefilled with detected text.
- Disc details form for plastic type, weight, color, condition, and notes — used by both the photo-ID flow and the catalog Add to my shelf action.
- Local photo storage in app-private directory plus a FileProvider for sharing.
- Training-data submission queue: when the photo-ID falls back to manual search and the user picks a disc, the photo plus extracted tokens plus confirmed disc id are saved locally, ready for an opt-in cloud upload in a later phase.
- Unit tests for the matcher (Jaro–Winkler, token extractor, decision engine).
```

Push, verify CI, tag.

Commit: `docs: tag 0.5.0 — Plan 5 photo-ID complete`

---

## Verification Checklist

- [ ] All 7 task commits land
- [ ] `assemblePlaystoreDebug` green
- [ ] All unit tests pass (5 prior + 9 ml/JaroWinkler + 5 ml/TokenExtractor + 4 ml/DiscMatcher = 23 unit tests)
- [ ] Lint clean
- [ ] CI auto-merge + release green
- [ ] CHANGELOG v0.5.0 tagged

---

## Out of Scope

- **Plan 6:** Lifecycle features (lost-disc map, wishlist, comparison)
- **Plan 7:** Stats + Settings + Onboarding
- **Plan 8:** Release pipeline
- Color-hue tiebreaker for ambiguous matches → Plan 5.5 if observed needed
- Photo carousel on owned disc detail → Plan 6 or later
- Trained vision classifier (TFLite) → Plan 9+ once training data accumulates

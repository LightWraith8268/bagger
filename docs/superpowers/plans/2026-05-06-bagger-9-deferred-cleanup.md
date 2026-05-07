# Bagger Plan 9 — Deferred Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Knock out deferreds from Plans 1-8: brand email/URL updates per new CLAUDE.md routing rules, edge-to-edge migration (drops `statusBarColor` deprecation), photo carousel on owned disc detail (wire `OwnedDiscPhoto` entity already in schema), color-hue tiebreaker in photo-ID matcher, Robolectric 4.15 (drop JUnit 4 + Vintage), KSP2 attempt.

**Architecture:** All in-app changes. No new screens, mostly refactors + extending existing flows.

---

## Task 1: Brand Email + URL Updates (per updated CLAUDE.md)

CLAUDE.md now specifies:
- `support@inknironapps.com` for feedback/bug reports
- `privacy@inknironapps.com` for privacy policy contact
- `security@inknironapps.com` for vulnerability reports
- Privacy/Terms URLs at `inknironapps.com/...` not GitHub Pages
- Subject prefill on mailto: `<AppName>%20feedback%20...`

Files:
- `app/src/main/java/com/inknironapps/bagger/ui/screens/settings/SettingsScreen.kt`

Replace existing:
- "Send feedback" row → `mailto:support@inknironapps.com?subject=Bagger%20feedback&body=Version:%20${state.versionName}%0A%0A`
- Add new "Report a bug" row → `mailto:support@inknironapps.com?subject=Bagger%20bug%20report&body=Version:%20${state.versionName}%0AAndroid:%20<api>%0A%0A`
- Add new "Report security issue" row → `mailto:security@inknironapps.com?subject=Bagger%20security`
- Privacy policy URL → `https://inknironapps.com/privacy-policy.html`
- Terms URL → `https://inknironapps.com/terms.html`

Helper composable for the mailto rows since the encoding is verbose. Create `MailtoRow` private composable inside SettingsScreen.kt.

```kotlin
@Composable
private fun MailtoRow(
    label: String,
    address: String,
    subject: String,
    body: String,
    context: android.content.Context,
    onError: (String) -> Unit
) {
    val encodedSubject = java.net.URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
    val encodedBody = java.net.URLEncoder.encode(body, "UTF-8").replace("+", "%20")
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier.clickable {
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = "mailto:$address?subject=$encodedSubject&body=$encodedBody".toUri()
            }
            try { context.startActivity(intent) }
            catch (_: android.content.ActivityNotFoundException) { onError("No email app installed.") }
        }
    )
}
```

Use it for all 3 email rows.

Commit: `feat(brand): update Settings emails + URLs per new routing rules`

---

## Task 2: Edge-to-Edge Migration

Drops `statusBarColor` deprecation warning from Plan 1's `Theme.kt`. Adopts `enableEdgeToEdge()` in `MainActivity.onCreate`. Compose Scaffold + insets handle drawing under system bars.

Files:
- `app/src/main/java/com/inknironapps/bagger/MainActivity.kt`
- `app/src/main/java/com/inknironapps/bagger/ui/theme/Theme.kt`

Changes:

1. In `MainActivity.kt`, add `enableEdgeToEdge()` call before `setContent`:

```kotlin
import androidx.activity.enableEdgeToEdge
// ...
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { /* ... */ }
}
```

2. In `Theme.kt`, delete the entire `SideEffect { ... }` block + the `view.context as Activity` cast + the `WindowCompat` import + the `toArgb` import. Theme becomes:

```kotlin
@Composable
fun BaggerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = BaggerTypography, content = content)
}
```

Plus remove unused imports (`Activity`, `SideEffect`, `LocalView`, `toArgb`, `WindowCompat`).

3. The existing `Scaffold(bottomBar = ...)` in `MainActivity` already passes `padding` to its content via `padding ->`. That handles the system-bars insets correctly under edge-to-edge.

4. Detail screens (`OwnedDiscDetailScreen`, etc.) that already use `Scaffold(topBar = ..., padding ->)` are fine — `Scaffold` consumes window insets correctly when `enableEdgeToEdge()` is on.

Commit: `feat(ui): migrate to edge-to-edge, drop statusBarColor deprecation`

---

## Task 3: Photo Carousel on Owned Disc Detail

`OwnedDiscPhoto` entity already exists from Plan 1. Wire CRUD: take/select photo from disc detail screen, store via `PhotoStorage`, render carousel.

Files:
- `app/src/main/java/com/inknironapps/bagger/data/db/dao/OwnedDiscPhotoDao.kt` (new)
- `app/src/main/java/com/inknironapps/bagger/data/db/BaggerDatabase.kt` (add accessor)
- `app/src/main/java/com/inknironapps/bagger/di/DatabaseModule.kt` (add @Provides)
- `app/src/main/java/com/inknironapps/bagger/domain/repo/OwnedDiscPhotoRepository.kt` (new)
- `app/src/main/java/com/inknironapps/bagger/data/repo/OwnedDiscPhotoRepositoryImpl.kt` (new)
- `app/src/main/java/com/inknironapps/bagger/di/RepoModule.kt` (add binding)
- `app/src/main/java/com/inknironapps/bagger/ui/screens/disc_detail/OwnedDiscDetailViewModel.kt` (extend)
- `app/src/main/java/com/inknironapps/bagger/ui/screens/disc_detail/OwnedDiscDetailScreen.kt` (add carousel + add photo button)

```kotlin
// OwnedDiscPhotoDao.kt
package com.inknironapps.bagger.data.db.dao

import androidx.room.*
import com.inknironapps.bagger.data.db.entity.OwnedDiscPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedDiscPhotoDao {
    @Upsert suspend fun upsert(photo: OwnedDiscPhotoEntity)
    @Delete suspend fun delete(photo: OwnedDiscPhotoEntity)
    @Query("SELECT * FROM owned_disc_photos WHERE ownedDiscId = :discId ORDER BY capturedAt DESC")
    fun observeForDisc(discId: String): Flow<List<OwnedDiscPhotoEntity>>
}
```

```kotlin
// BaggerDatabase.kt — add abstract method
abstract fun ownedDiscPhotoDao(): OwnedDiscPhotoDao
```

```kotlin
// DatabaseModule.kt — add @Provides
@Provides fun provideOwnedDiscPhotoDao(db: BaggerDatabase): OwnedDiscPhotoDao = db.ownedDiscPhotoDao()
```

```kotlin
// OwnedDiscPhotoRepository.kt
package com.inknironapps.bagger.domain.repo

import com.inknironapps.bagger.data.db.entity.OwnedDiscPhotoEntity
import kotlinx.coroutines.flow.Flow

interface OwnedDiscPhotoRepository {
    fun observeForDisc(discId: String): Flow<List<OwnedDiscPhotoEntity>>
    suspend fun upsert(photo: OwnedDiscPhotoEntity)
    suspend fun delete(photo: OwnedDiscPhotoEntity)
}
```

```kotlin
// OwnedDiscPhotoRepositoryImpl.kt
package com.inknironapps.bagger.data.repo

import com.inknironapps.bagger.data.db.dao.OwnedDiscPhotoDao
import com.inknironapps.bagger.data.db.entity.OwnedDiscPhotoEntity
import com.inknironapps.bagger.domain.repo.OwnedDiscPhotoRepository
import javax.inject.Inject

class OwnedDiscPhotoRepositoryImpl @Inject constructor(
    private val dao: OwnedDiscPhotoDao
) : OwnedDiscPhotoRepository {
    override fun observeForDisc(discId: String) = dao.observeForDisc(discId)
    override suspend fun upsert(photo: OwnedDiscPhotoEntity) = dao.upsert(photo)
    override suspend fun delete(photo: OwnedDiscPhotoEntity) = dao.delete(photo)
}
```

```kotlin
// RepoModule.kt — add binding
@Binds @Singleton abstract fun bindPhotos(impl: OwnedDiscPhotoRepositoryImpl): OwnedDiscPhotoRepository
```

Extend `OwnedDiscDetailViewModel.kt`:

```kotlin
// inject:
private val photoRepo: OwnedDiscPhotoRepository,
private val photoStorage: PhotoStorage,

// add to ui state:
data class OwnedDetailUi(
    val owned: OwnedDiscEntity? = null,
    val catalog: DiscEntity? = null,
    val bags: List<BagEntity> = emptyList(),
    val photos: List<OwnedDiscPhotoEntity> = emptyList()
)

// extend the combine to include photoRepo.observeForDisc(ownedId):
// (since the disc id is fixed for this VM, we can join in the StateFlow chain)

// add methods:
fun addPhotoFromUri(uri: android.net.Uri, type: String) {
    viewModelScope.launch {
        val bitmap = ... decode from uri ... ?: return@launch
        val file = photoStorage.savePhoto(bitmap)
        photoRepo.upsert(OwnedDiscPhotoEntity(
            id = UUID.randomUUID().toString(),
            ownedDiscId = ownedId,
            localPath = file.absolutePath,
            type = type,
            capturedAt = System.currentTimeMillis()
        ))
    }
}

fun deletePhoto(photo: OwnedDiscPhotoEntity) {
    viewModelScope.launch {
        photoRepo.delete(photo)
        photoStorage.delete(photo.localPath)
    }
}
```

Update `OwnedDiscDetailScreen.kt` — add photo carousel above metadata. Use Coil's `AsyncImage` (already on classpath from Plan 5).

```kotlin
// inside Column { ... }, just below TopAppBar:
if (state.photos.isNotEmpty()) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.photos, key = { it.id }) { photo ->
            Card(
                modifier = Modifier.size(width = 160.dp, height = 160.dp),
                onClick = { /* lightbox in a future plan */ }
            ) {
                coil.compose.AsyncImage(
                    model = java.io.File(photo.localPath),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// "Add photo" FAB-style button (use rememberLauncherForActivityResult w/ ActivityResultContracts.GetContent):
val addPhotoLauncher = rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.GetContent()
) { uri ->
    uri?.let { vm.addPhotoFromUri(it, "Front") }
}
// Add row of buttons below metadata:
Button(onClick = { addPhotoLauncher.launch("image/*") }) { Text("Add photo") }
```

`addPhotoFromUri` needs to decode `Uri` → `Bitmap`. Use `ContentResolver`:

```kotlin
// inside ViewModel — switch the addPhotoFromUri to take context too via @ApplicationContext
fun addPhotoFromUri(uri: android.net.Uri, type: String) {
    viewModelScope.launch {
        try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            } ?: return@launch
            val file = photoStorage.savePhoto(bitmap)
            photoRepo.upsert(OwnedDiscPhotoEntity(
                id = UUID.randomUUID().toString(),
                ownedDiscId = ownedId,
                localPath = file.absolutePath,
                type = type,
                capturedAt = System.currentTimeMillis()
            ))
        } catch (_: Exception) {}
    }
}
```

Inject `@ApplicationContext context: Context` into `OwnedDiscDetailViewModel`.

Commit: `feat: add photo carousel on owned disc detail w/ add-photo flow`

---

## Task 4: Color-Hue Tiebreaker for Photo-ID

Adds simple dominant-hue extraction during photo capture. Used as a tiebreaker when matcher returns close candidates. Still pure app-side, no server.

Files:
- `app/src/main/java/com/inknironapps/bagger/ml/ColorExtractor.kt` (new)
- Test: `app/src/test/java/com/inknironapps/bagger/ml/ColorExtractorTest.kt`

```kotlin
// ColorExtractor.kt
package com.inknironapps.bagger.ml

import android.graphics.Bitmap

/**
 * Extracts a dominant color from the center 100×100 region of a bitmap.
 * Used as a tiebreaker hint for ambiguous OCR matches.
 */
object ColorExtractor {

    data class DominantColor(val r: Int, val g: Int, val b: Int) {
        fun toHex(): String = "#%02X%02X%02X".format(r, g, b)
    }

    fun extractCenter(bitmap: Bitmap, sampleSize: Int = 100): DominantColor {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        val half = sampleSize / 2
        val left = (cx - half).coerceAtLeast(0)
        val top = (cy - half).coerceAtLeast(0)
        val right = (cx + half).coerceAtMost(bitmap.width)
        val bottom = (cy + half).coerceAtMost(bitmap.height)

        var rTotal = 0L; var gTotal = 0L; var bTotal = 0L; var n = 0L
        for (y in top until bottom step 4) {  // step 4 for speed
            for (x in left until right step 4) {
                val pixel = bitmap.getPixel(x, y)
                rTotal += (pixel shr 16) and 0xFF
                gTotal += (pixel shr 8) and 0xFF
                bTotal += pixel and 0xFF
                n++
            }
        }
        if (n == 0L) return DominantColor(0, 0, 0)
        return DominantColor((rTotal / n).toInt(), (gTotal / n).toInt(), (bTotal / n).toInt())
    }
}
```

Test:

```kotlin
// ColorExtractorTest.kt
package com.inknironapps.bagger.ml

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ColorExtractorTest {

    @Test fun extractsDominantColorFromSolidBitmap() {
        val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(220, 30, 40))
        }
        val color = ColorExtractor.extractCenter(bmp)
        assertTrue(color.r in 215..225)
        assertTrue(color.g in 25..35)
        assertTrue(color.b in 35..45)
    }

    @Test fun handlesSmallerBitmapWithoutCrash() {
        val bmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(0, 0, 255))
        }
        val color = ColorExtractor.extractCenter(bmp)
        assertTrue(color.b > 200)
    }
}
```

Wire into `AddDiscViewModel.onPhotoCapturedNoContext` — store extracted hex in `AddDiscState` (new field `dominantColorHex: String?`), pre-fill the `color` field of the details form with it.

```kotlin
// AddDiscState.kt — add:
val dominantColorHex: String? = null

// AddDiscViewModel.kt — in onPhotoCapturedNoContext:
val bitmap = ...
val color = ColorExtractor.extractCenter(bitmap)
_state.value = _state.value.copy(dominantColorHex = color.toHex(), color = color.toHex())
// then continue w/ OCR + matcher as before
```

(The user can edit the prefilled value in the form.)

Commit: `feat(ml): add color-hue extractor + prefill color field in add-disc form`

---

## Task 5: Robolectric 4.15 + Drop JUnit 4 / Vintage

Bumps Robolectric. Switches `BaggerPrefsTest` from `@RunWith(RobolectricTestRunner::class)` (JUnit 4) to `@ExtendWith(RobolectricExtension::class)` (JUnit 5). Drops `junit:junit:4.13.2` and `junit-vintage-engine` deps.

Files:
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/test/java/com/inknironapps/bagger/data/prefs/BaggerPrefsTest.kt`

Changes:

```toml
# libs.versions.toml — bump:
robolectric = "4.15.1"

[libraries]
robolectric-junit5 = { module = "org.robolectric:junit5", version.ref = "robolectric" }
```

```kotlin
// app/build.gradle.kts — remove these two:
testImplementation("junit:junit:4.13.2")
testImplementation("org.junit.vintage:junit-vintage-engine:5.11.4")
// add:
testImplementation(libs.robolectric.junit5)
```

```kotlin
// BaggerPrefsTest.kt — replace @RunWith with @ExtendWith
package com.inknironapps.bagger.data.prefs

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.junit5.RobolectricExtension
import kotlin.test.assertEquals

@ExtendWith(RobolectricExtension::class)
class BaggerPrefsTest {
    @BeforeEach
    fun clearPrefs() {
        kotlinx.coroutines.runBlocking {
            BaggerPrefs(ApplicationProvider.getApplicationContext())
                .dataStoreFor(ApplicationProvider.getApplicationContext()).edit { it.clear() }
        }
    }

    // ... existing tests w/ org.junit.jupiter.api.Test imports
}
```

Verify: `./gradlew :app:testPlaystoreDebugUnitTest` — all tests still pass.

Commit: `chore(test): bump Robolectric to 4.15 + switch to JUnit 5 extension`

---

## Task 6: KSP2 Attempt

Hilt 2.54 + Kotlin 2.1 may now support KSP2. Try flipping `ksp.useKSP2=true` in `gradle.properties`. Build. If green, ship the change. If broken, revert immediately.

Files:
- `gradle.properties`

Change `ksp.useKSP2=false` → `ksp.useKSP2=true`. Run:

```bash
./gradlew :app:assemblePlaystoreDebug
```

If success: keep the change. Update the `# TODO` comment to reflect status.
If failure: revert and skip this commit (no harm done, KSP1 still works).

Commit (if successful): `chore: re-enable KSP2 — Hilt 2.54 supports it`

---

## Task 7: Push + CHANGELOG v1.0.0-rc.2

Append to `CHANGELOG.md`:

```markdown
## [1.0.0-rc.2] - 2026-05-06

### Changed

- Settings screen email rows now route to the correct addresses per the brand routing table: Send feedback and Report a bug send to `support@inknironapps.com` with subject and version pre-filled, Report security issue sends to `security@inknironapps.com`. Privacy policy and terms links now point at `https://inknironapps.com/privacy-policy.html` and `https://inknironapps.com/terms.html`.
- The app now draws content edge-to-edge under the system status and navigation bars, using the modern `enableEdgeToEdge()` API instead of the deprecated `Window.statusBarColor` setter.
- Test infrastructure modernized: Robolectric bumped to 4.15.1 and BaggerPrefsTest now uses the JUnit 5 `RobolectricExtension`. Dropped the JUnit 4 plus Vintage Engine bridge that was carrying the old runner.

### Added

- Photo carousel on the owned disc detail screen. Tap Add photo to pick from your gallery; photos are stored privately to the app's data directory and never leave the device.
- The disc identification flow now extracts a dominant color from the center of each captured photo and pre-fills the color field of the details form with the matching hex value.
```

Push, verify CI green, tag.

---

## Verification

- [ ] All 6 task commits land
- [ ] Build green
- [ ] Lint clean
- [ ] Unit tests: 22 prior + 2 ColorExtractor = 24 passing
- [ ] CI auto-merge + release green
- [ ] CHANGELOG v1.0.0-rc.2 tagged

---

## Out of Scope (Tracked)

- Real launcher icon (I&I monogram) — needs brand SVG
- Auto-trigger release.yml after auto-merge — needs PAT setup by user
- Real per-manufacturer scrapers (Plan 2 stubs) — site-specific iteration
- PDGA detail-page hydration for max weight/diameter (not in current schema)
- Lightbox / full-screen photo view (Plan 10+)
- Photo capture directly from disc detail (currently only via gallery picker)

# Bagger Plan 10 — Real Deferreds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Pull genuinely-doable items off the deferred list. Real launcher icon from brand kit. Auto-trigger release.yml after auto-merge via `gh workflow run` (GITHUB_TOKEN's `actions: write` scope, no PAT). PDGA detail-page hydration (schema v2). Per-mfr scraper iteration (best-effort real-site adaptation). Photo lightbox. Photo capture from disc detail (shared camera composable).

**Architecture:** Mostly polish + extending existing flows. Brand-asset copy from `D:\Coding\inkironapps\brand\android\exports\`. Workflow-to-workflow trigger via `gh workflow run` inside auto-merge step. Schema bump via additive optional fields (backward compatible).

---

## Task 1: Real Launcher Icon From Brand Kit

Files:
- Copy from `D:\Coding\inkironapps\brand\android\exports\<dpi>/ic_launcher_*.png` → `app/src/main/res/mipmap-<dpi>/`
- Replace `app/src/main/res/drawable/ic_launcher_foreground.xml` (placeholder vector) with `ic_launcher_foreground.svg` converted to Android vector drawable — OR keep the placeholder XML deleted and use PNG-only foreground via mipmap exports.
- Update `app/src/main/res/values/ic_launcher_background.xml` if brand has a canonical hex (check `colors.xml` in brand kit)
- Keep `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` adaptive-icon wrappers pointing at the new background color + foreground drawable

Steps:

1. Read `D:\Coding\inkironapps\brand\android\colors.xml` to get the canonical `ic_launcher_background` color. If it differs from `#095F73`, update Bagger's value to match brand.

2. Copy PNG launcher exports:
```bash
for dpi in hdpi mdpi xhdpi xxhdpi xxxhdpi; do
  mkdir -p app/src/main/res/mipmap-$dpi
  cp "/d/Coding/inkironapps/brand/android/exports/$dpi/ic_launcher.png" "app/src/main/res/mipmap-$dpi/"
  cp "/d/Coding/inkironapps/brand/android/exports/$dpi/ic_launcher_foreground.png" "app/src/main/res/mipmap-$dpi/"
done
```

3. Update `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` so foreground references `@mipmap/ic_launcher_foreground` (PNG) instead of `@drawable/ic_launcher_foreground` (placeholder vector). Now the launcher uses real brand art on API 26+ and PNG legacy fallback on older systems (unused since minSdk=31, but harmless).

4. Optionally delete `app/src/main/res/drawable/ic_launcher_foreground.xml` placeholder. The adaptive-icon now resolves to `@mipmap/ic_launcher_foreground`.

5. Verify by reinstalling on emulator — the launcher icon in app drawer should now show the I&I monogram.

Commit: `feat(brand): use real I&I launcher icon from brand kit`

---

## Task 2: Auto-Dispatch release.yml From auto-merge.yml (No PAT)

GitHub Actions' default `GITHUB_TOKEN` has `actions: write` permission when explicitly granted. Add a step at the end of `auto-merge.yml` that dispatches `release.yml` on the just-merged main branch.

Files:
- `.github/workflows/auto-merge.yml`

Add to `permissions:` block:
```yaml
permissions:
  contents: write
  pull-requests: write
  actions: write          # NEW — allows dispatching other workflows
```

Add as final step (after "Recreate claude/dev from main"):

```yaml
      - name: Trigger release build on main
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          gh workflow run release.yml --ref main
          echo "Release build dispatched."
```

That's it. No PAT needed — the workflow's GITHUB_TOKEN can dispatch workflows in the same repo with `actions: write`.

Commit: `ci: auto-dispatch release.yml after auto-merge (no PAT needed)`

---

## Task 3: Photo Lightbox

Tap any photo in the owned disc detail carousel → opens a full-screen viewer w/ zoom + dismiss. Plain Compose, no external lib.

Files:
- New: `app/src/main/java/com/inknironapps/bagger/ui/components/PhotoLightbox.kt`
- Modify: `app/src/main/java/com/inknironapps/bagger/ui/screens/disc_detail/OwnedDiscDetailScreen.kt`

```kotlin
// PhotoLightbox.kt
package com.inknironapps.bagger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

@Composable
fun PhotoLightbox(photoPath: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = photoPath != null, enter = fadeIn(), exit = fadeOut()) {
        photoPath ?: return@AnimatedVisibility
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            ) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White)
                }
            }
        }
    }
}
```

In `OwnedDiscDetailScreen.kt`, add state + wire on each carousel `Card`:

```kotlin
var lightboxPath by remember { mutableStateOf<String?>(null) }
// existing carousel Card onClick:
onClick = { lightboxPath = photo.localPath }
// at end of screen body:
PhotoLightbox(photoPath = lightboxPath, onDismiss = { lightboxPath = null })
```

Commit: `feat(ui): add photo lightbox w/ pinch-zoom + tap-to-dismiss`

---

## Task 4: Shared Camera Composable + Capture-From-Disc-Detail

Refactor the camera capture out of `AddDisc/CameraStep.kt` into a reusable composable. Add capture button to `OwnedDiscDetailScreen` that opens it, saves photo, attaches to disc.

Files:
- New: `app/src/main/java/com/inknironapps/bagger/ui/components/CameraCapture.kt` (extracted)
- Modify: `app/src/main/java/com/inknironapps/bagger/ui/screens/add_disc/CameraStep.kt` (delegates to component)
- Modify: `app/src/main/java/com/inknironapps/bagger/ui/screens/disc_detail/OwnedDiscDetailScreen.kt` (adds "Take photo" button)
- Modify: `app/src/main/java/com/inknironapps/bagger/ui/screens/disc_detail/OwnedDiscDetailViewModel.kt` (already has `addPhotoFromUri` from Plan 9 — add `addPhotoFromFile` variant)

Extract the existing camera code from Plan 5's `CameraStep.kt` into:

```kotlin
// CameraCapture.kt
package com.inknironapps.bagger.ui.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.inknironapps.bagger.data.photo.PhotoStorage
import dagger.hilt.android.EntryPointAccessors
import com.inknironapps.bagger.di.PhotoStorageEntryPoint
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraCapture(
    onPhoto: (File) -> Unit,
    processing: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val perm = rememberPermissionState(Manifest.permission.CAMERA)
    when {
        perm.status.isGranted -> CameraView(onPhoto, processing, modifier)
        else -> PermissionPrompt(onGrant = { perm.launchPermissionRequest() }, modifier = modifier)
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera permission needed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("To capture a disc photo, Bagger needs to use your camera.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Grant permission") }
    }
}

@Composable
private fun CameraView(onPhoto: (File) -> Unit, processing: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val photoStorage = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, PhotoStorageEntryPoint::class.java
        ).photoStorage()
    }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    Box(modifier) {
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
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
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
                capture.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) { onPhoto(file) }
                        override fun onError(exception: ImageCaptureException) {}
                    }
                )
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

Reduce `CameraStep.kt` to:

```kotlin
package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.runtime.Composable
import com.inknironapps.bagger.ui.components.CameraCapture
import java.io.File

@Composable
fun CameraStep(onPhoto: (File) -> Unit, processing: Boolean) {
    CameraCapture(onPhoto = onPhoto, processing = processing)
}
```

Update `OwnedDiscDetailViewModel.kt` — add a method that takes a `File`:

```kotlin
fun addPhotoFromFile(file: File, type: String = "Front") {
    viewModelScope.launch {
        photoRepo.upsert(OwnedDiscPhotoEntity(
            id = UUID.randomUUID().toString(),
            ownedDiscId = ownedId,
            localPath = file.absolutePath,
            type = type,
            capturedAt = System.currentTimeMillis()
        ))
    }
}
```

Update `OwnedDiscDetailScreen.kt` — add a "Take photo" button that toggles a full-screen camera state:

```kotlin
var showCamera by remember { mutableStateOf(false) }
// existing "Add photo" button stays (gallery picker); add new button:
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = { addPhotoLauncher.launch("image/*") }) { Text("Pick from gallery") }
    Button(onClick = { showCamera = true }) { Text("Take photo") }
}

// at end of screen:
if (showCamera) {
    Dialog(
        onDismissRequest = { showCamera = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraCapture(
                onPhoto = { file ->
                    vm.addPhotoFromFile(file)
                    showCamera = false
                }
            )
            IconButton(onClick = { showCamera = false }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
        }
    }
}
```

Commit: `feat(ui): extract shared CameraCapture composable + add Take photo on disc detail`

---

## Task 5: PDGA Detail-Page Hydration (Schema v2)

Pull max-weight, diameter, and disc class from PDGA per-disc detail pages. Schema bumps to v2 with new optional fields. App-side: tolerate v1 and v2 (the existing schema check accepts <= SUPPORTED_SCHEMA_VERSION).

Bump `SUPPORTED_SCHEMA_VERSION` to 2 in `DiscDbSyncWorker`. Add `maxWeightG: Float?` and `diameterCm: Float?` to `DiscDto` and `DiscEntity`. Room migration.

This is significant — schema migration requires Room migration code. Doable but takes care.

Files:
- `data/schema.json` — add new optional fields
- `data/scripts/scrape_pdga.py` — fetch detail pages, parse weight/diameter/class
- `data/scripts/enrich.py` — pass through new fields
- `data/scripts/manual_overrides.json` — fields default to null; existing entries unchanged
- `data/discs.json` — regenerated
- `app/src/main/java/com/inknironapps/bagger/data/db/entity/DiscEntity.kt` — add fields
- `app/src/main/java/com/inknironapps/bagger/data/db/BaggerDatabase.kt` — version → 2 + migration
- `app/src/main/java/com/inknironapps/bagger/data/db/MigrationsModule.kt` (new) — Room migration 1→2
- `app/src/main/java/com/inknironapps/bagger/di/DatabaseModule.kt` — register migrations
- `app/src/main/java/com/inknironapps/bagger/data/remote/DiscDto.kt` — add fields
- `app/src/main/java/com/inknironapps/bagger/data/sync/DiscDbSyncWorker.kt` — bump SUPPORTED_SCHEMA_VERSION
- `data/scripts/build_baseline.py` regenerates `discs-baseline.json` w/ new fields

### Schema v2

Add to `data/schema.json` properties block:
```json
"maxWeightG": { "type": ["number", "null"], "minimum": 100, "maximum": 250 },
"diameterCm": { "type": ["number", "null"], "minimum": 15, "maximum": 25 },
"discClass": { "type": ["string", "null"] }
```

Update `schemaVersion`:
```json
"schemaVersion": { "type": "integer", "minimum": 1, "maximum": 2 }
```

(Allow both 1 and 2 — sync worker reads whichever matches.)

### Hydration script extension

In `data/scripts/scrape_pdga.py`, add detail-page fetcher:

```python
def fetch_detail_page(detail_url: str, session: requests.Session) -> dict:
    """Pull max weight + diameter from PDGA per-disc detail page."""
    resp = session.get(detail_url, timeout=30)
    resp.raise_for_status()
    soup = BeautifulSoup(resp.text, "lxml")
    out = {"max_weight_g": None, "diameter_cm": None, "disc_class": None}
    # PDGA detail pages have a "Specifications" block; spec rows have labels + values.
    for row in soup.select("div.field-name-field-disc-class-tax, div.field-name-field-maximum-weight, div.field-name-field-diameter"):
        label_el = row.select_one(".field-label")
        value_el = row.select_one(".field-item")
        if not label_el or not value_el:
            continue
        label = label_el.get_text(strip=True).lower().rstrip(":")
        value = value_el.get_text(strip=True)
        if "class" in label:
            out["disc_class"] = value
        elif "weight" in label:
            out["max_weight_g"] = _parse_float(value, " g")
        elif "diameter" in label:
            out["diameter_cm"] = _parse_float(value, " cm")
    return out
```

In the main scrape loop, after the listing-page parse:

```python
def main() -> None:
    # ...existing args + setup
    all_rows: list[dict] = []
    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT
    for html in fetch_all_pages(max_pages=args.max_pages):
        rows = parse_pdga_table(html)
        for row in rows:
            detail_url = row.get("detail_url")
            if detail_url and args.hydrate:
                try:
                    detail = fetch_detail_page(detail_url, session)
                    row.update(detail)
                except Exception as e:
                    print(f"detail fetch failed for {detail_url}: {e}")
                time.sleep(SLEEP_SECONDS)
        all_rows.extend(rows)
    # ...
```

Add `--hydrate` flag to opt in (skips ~1700 extra requests when not needed).

In `enrich.py`, pass through the new fields:

```python
out.append({
    # ... existing fields
    "maxWeightG": p.get("max_weight_g"),
    "diameterCm": p.get("diameter_cm"),
    "discClass": p.get("disc_class"),
    "schemaVersion": 2,
})
```

(Bump default `schemaVersion` to 2.)

### Room migration

In `BaggerDatabase.kt`:

```kotlin
@Database(
    entities = [ /* same as before */ ],
    version = 2,
    exportSchema = true
)
```

Add `DiscEntity` fields (all nullable):

```kotlin
val maxWeightG: Float?,
val diameterCm: Float?,
val discClass: String?
```

(Defaults to `null` on existing records via migration.)

Create `data/db/migrations/Migration_1_2.kt`:

```kotlin
package com.inknironapps.bagger.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE discs ADD COLUMN maxWeightG REAL")
        db.execSQL("ALTER TABLE discs ADD COLUMN diameterCm REAL")
        db.execSQL("ALTER TABLE discs ADD COLUMN discClass TEXT")
    }
}
```

Register in `DatabaseModule.kt`:

```kotlin
Room.databaseBuilder(context, BaggerDatabase::class.java, "bagger.db")
    .addMigrations(MIGRATION_1_2)
    .fallbackToDestructiveMigrationOnDowngrade(true)
    .build()
```

### DiscDto + sync worker

`DiscDto.kt`:
```kotlin
val maxWeightG: Float? = null,
val diameterCm: Float? = null,
val discClass: String? = null,
```

`DiscDbSyncWorker.kt`:
```kotlin
const val SUPPORTED_SCHEMA_VERSION = 2
```

And update `toEntity()`:
```kotlin
private fun DiscDto.toEntity() = DiscEntity(
    // ... existing
    maxWeightG = maxWeightG,
    diameterCm = diameterCm,
    discClass = discClass
)
```

### Update fixture loader

`BaselineDiscLoader.kt`:
```kotlin
maxWeightG = if (o.has("maxWeightG") && !o.isNull("maxWeightG")) o.getDouble("maxWeightG").toFloat() else null,
diameterCm = if (o.has("diameterCm") && !o.isNull("diameterCm")) o.getDouble("diameterCm").toFloat() else null,
discClass = if (o.has("discClass") && !o.isNull("discClass")) o.getString("discClass") else null
```

### Run pipeline

```bash
cd data/scripts
.venv/Scripts/python.exe scrape_pdga.py --hydrate --max-pages 100
.venv/Scripts/python.exe enrich.py
.venv/Scripts/python.exe validate.py --no-net
.venv/Scripts/python.exe build_baseline.py
```

(Note: `--hydrate` will take ~30 minutes due to 1700+ extra requests at 1/sec rate limit. Acceptable for one-off; CI doesn't run hydration.)

### Migration test

Add an instrumented test to verify v1 → v2 migration works:

```kotlin
// app/src/androidTest/java/com/inknironapps/bagger/data/db/MigrationTest.kt
package com.inknironapps.bagger.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inknironapps.bagger.data.db.migrations.MIGRATION_1_2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BaggerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun migrate1To2_addsNullableColumns() {
        helper.createDatabase("test.db", 1).use { db ->
            db.execSQL("""
                INSERT INTO discs (id, brand, mold, speed, glide, turn, fade, discType, stability, pdgaApproved, yearReleased, primaryStampUrl)
                VALUES ('innova-aviar', 'Innova', 'Aviar', 2.0, 3.0, 0.0, 1.0, 'Putter', 'stable', 1, 1985, NULL)
            """)
        }
        helper.runMigrationsAndValidate("test.db", 2, true, MIGRATION_1_2).use { db ->
            val cursor = db.query("SELECT maxWeightG, diameterCm, discClass FROM discs WHERE id = 'innova-aviar'")
            assertNotNull(cursor)
            cursor.moveToFirst()
            // All three new columns should be NULL after migration
            assert(cursor.isNull(0))
            assert(cursor.isNull(1))
            assert(cursor.isNull(2))
        }
    }
}
```

Commit: `feat(data): bump schema to v2 with PDGA detail fields + Room migration 1→2`

(Skip hydration run if PDGA detail pages have unpredictable HTML — just bump schema with empty fields. Live hydration is opt-in via `--hydrate`.)

---

## Task 6: Per-Mfr Scraper Iteration (Best-Effort)

For Innova and Discraft (the top 2 brands), fetch real index pages, capture HTML samples, adapt parsers. For brands where it doesn't work cleanly, mark as `TODO(scraper): manual fixture needed` and move on. This is best-effort exploration.

Steps:

1. Run a curl probe for each manufacturer:
```bash
mkdir -p /tmp/mfr-probes
for brand in innova discraft mvp dynamic_discs latitude64 discmania prodigy westside; do
  # Try the URL the stub scraper used
  case "$brand" in
    innova) URL="https://www.innovadiscs.com/disc-golf-discs/all-discs" ;;
    discraft) URL="https://www.discraft.com/collections/all-discs" ;;
    mvp) URL="https://mvpdiscsports.com/collections/discs" ;;
    dynamic_discs) URL="https://dynamicdiscs.com/collections/discs" ;;
    latitude64) URL="https://latitude64.se/discs/" ;;
    discmania) URL="https://discmania.com/collections/discs" ;;
    prodigy) URL="https://www.prodigydisc.com/collections/discs" ;;
    westside) URL="https://westsidediscgolf.com/collections/discs" ;;
  esac
  echo "=== $brand: $URL ==="
  curl -sL -A "BaggerDiscDb/1.0 (+https://github.com/LightWraith8268/bagger)" \
    "$URL" -o /tmp/mfr-probes/$brand-index.html
  wc -l /tmp/mfr-probes/$brand-index.html
done
```

2. For each, eyeball the structure (look for product links pattern). If the index lists products → identify the product detail URL pattern. Fetch one product page.

3. For each tractable brand, replace the `scrape()` method's `return []` with a real implementation that:
   - Parses the index
   - Yields parsed `MfrDisc` records by fetching each product page

4. If a brand uses heavy JS (`<noscript>` content empty, page mostly empty without JS), tag it `TODO(scraper)` and leave the stub.

This is the messy real-world part. Output not guaranteed for all 8 brands; aim for 2-3 working scrapers and acknowledge the rest.

Commit: `feat(data): real scrapers for tractable brands (incremental)`

---

## Task 7: Push + CHANGELOG v1.0.0-rc.3

```markdown
## [1.0.0-rc.3] - 2026-05-10

### Added

- Real Ink and Iron Apps launcher icon shipping in all five display densities, replacing the placeholder vector.
- Photo lightbox: tap any photo on a disc detail screen to view it full-screen with pinch to zoom and tap-to-dismiss.
- Take photo button on the owned disc detail screen alongside the existing Pick from gallery — captures via CameraX into the same per-disc photo collection.

### Changed

- Disc catalog schema bumped to version 2. New optional fields: max weight in grams, diameter in centimeters, and disc class string sourced from PDGA per-disc detail pages. The app reads both v1 and v2 catalog feeds.
- Local database migrates from v1 to v2 automatically on first launch after upgrade, adding three nullable columns to the discs table.
- Continuous delivery: the auto-merge workflow now dispatches the release build workflow against the freshly-merged main branch, so signed AAB artifacts are produced for every claude/dev push without a manual workflow dispatch.

### Other

- Per-manufacturer scrapers iterated against real sites where tractable; brands that require JavaScript rendering remain stubs with TODO markers.
```

Push, verify CI green, tag.

---

## Verification

- [ ] All commits land
- [ ] Build green
- [ ] Lint clean
- [ ] Unit tests: 24 prior all pass
- [ ] Instrumented MigrationTest passes (if emulator available)
- [ ] CI: auto-merge + release green (release.yml now auto-dispatched after auto-merge)
- [ ] CHANGELOG v1.0.0-rc.3 tagged
- [ ] Launcher icon visually verified on emulator

---

## Out of Scope

- Keystore generation + GitHub secrets (user-gated, permanent commitment)
- `MAPS_API_KEY` + Play Console (user accounts)
- Real scrapers for JavaScript-heavy manufacturer sites (would need Playwright dep)

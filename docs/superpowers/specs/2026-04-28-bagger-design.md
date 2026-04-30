# Bagger — Design Spec

- **Date:** 2026-04-28
- **App:** Bagger (`com.inknironapps.bagger`)
- **Brand:** Ink & Iron Apps
- **Repo:** `lightwraith8268/bagger` (single repo: app + disc DB + docs)
- **License:** GPLv3
- **Platform:** Android only (phase 1)
- **Min SDK:** 31 (Android 12) · **Target SDK:** 35

## 1. Purpose

Catalog disc-golf discs via photo-ID. User snaps disc → app IDs via OCR → stored on shelf w/ flight #s. Manual search fallback. Bag mgmt (multi-bag), lost-disc tracking w/ GPS, wishlist, stats, backup, comparison.

Phase 1 = solo, local-only, no auth. Phase 2 = social (Firebase/Supabase). Phase 3 = trained vision model + UDisc CSV import. Data layer cloud-ready day 1 (UUIDs, timestamps, nullable userId).

## 2. Stack

- Kotlin · Jetpack Compose (Material 3)
- MVVM + Repository
- Coroutines + Flow
- Hilt (DI)
- Room (local SQLite) — owned discs, bags, cached disc DB
- DataStore (prefs)
- ML Kit Text Recognition v2 (on-device OCR)
- CameraX (capture)
- Retrofit + OkHttp (fetch `discs.json`)
- Coil (images)
- Google Maps Compose (lost-disc map)
- WorkManager (DB sync)

## 3. Module Layout

Single Gradle module phase 1, ready to split:

```
com.inknironapps.bagger/
├── data/        # Room entities, DAOs, repos, remote fetcher
├── domain/      # use cases, models (pure Kotlin, no Android deps)
├── ui/          # Compose screens + ViewModels per feature
├── ml/          # OCR + match logic (JaroWinkler, scorer)
└── di/          # Hilt modules
```

## 4. Data Model

### Local (Room)

```kotlin
@Entity Disc(                       // catalog entry from public DB
    id: String (UUID, PK),          // matches GitHub JSON id (slug)
    brand: String,
    mold: String,
    speed: Float, glide: Float,
    turn: Float, fade: Float,
    discType: Enum(Putter|Mid|Fairway|Driver|Approach),
    stability: String,              // "overstable"/"stable"/"understable"
    pdgaApproved: Boolean,
    yearReleased: Int?,
    primaryStampUrl: String?
)

@Entity OwnedDisc(                  // user's actual disc instance
    id: UUID (PK),
    discId: FK -> Disc.id,
    plasticType: String?,
    weight: Int?,                   // grams
    color: String?,                 // hex or name
    condition: Enum(New|Good|Beat|Dyed),
    state: Enum(Shelf|InBag|Lost|Found|Sold|Traded|Retired|Gifted),
    bagId: FK? -> Bag.id,           // populated when state=InBag
    purchaseDate: Long?,
    purchasePrice: Cents?,
    notes: String?,
    isOriginalOwner: Boolean,
    customTags: List<String>,       // JSON column
    createdAt: Long,
    updatedAt: Long,
    userId: String? = null,         // phase-2 cloud
    syncedAt: Long? = null
)

@Entity OwnedDiscPhoto(
    id: UUID (PK),
    ownedDiscId: FK,
    localPath: String,              // app-internal storage
    type: Enum(Front|Back|Stamp|Other),
    capturedAt: Long
)

@Entity Bag(
    id: UUID (PK),
    name: String,
    description: String?,
    iconColor: String,
    sortOrder: Int,
    createdAt, updatedAt, userId, syncedAt
)

@Entity LostDiscEvent(              // history per disc, supports re-loss
    id: UUID (PK),
    ownedDiscId: FK,
    lostAt: Long,
    lat: Double?, lng: Double?,
    courseName: String?,
    holeNumber: Int?,
    notes: String?,
    foundAt: Long? = null
)

@Entity WishlistItem(
    id: UUID (PK),
    discId: FK -> Disc.id,
    addedAt: Long,
    targetWeight: Int?,
    targetPlastic: String?,
    notes: String?
)

@Entity DiscDbMeta(
    id: Int = 1 (PK),
    lastSyncedAt: Long,
    etag: String?,
    discCount: Int,
    schemaVersion: Int
)

@Entity IdSubmissionQueue(          // training-data capture, deferred upload
    id: UUID (PK),
    photoPath: String,
    confirmedDiscId: String,
    ocrTokens: List<String>,        // JSON
    capturedAt: Long
)
```

**Indices:** `OwnedDisc(state, bagId)`, `OwnedDisc(discId)`, `LostDiscEvent(ownedDiscId, foundAt)`.

### Remote `discs.json` (GitHub-hosted)

```json
{
  "id": "innova-destroyer",
  "brand": "Innova",
  "mold": "Destroyer",
  "speed": 12, "glide": 5, "turn": -1, "fade": 3,
  "discType": "Driver",
  "stability": "overstable",
  "pdgaApproved": true,
  "yearReleased": 2008,
  "primaryStampUrl": "https://raw.githubusercontent.com/lightwraith8268/bagger/main/data/stamps/innova-destroyer.webp",
  "aliases": ["Destroyer GStar", "Star Destroyer"],
  "schemaVersion": 1
}
```

## 5. Repo Structure

```
bagger/
├── app/                              # Android Gradle module
├── data/
│   ├── discs.json
│   ├── schema.json
│   ├── CHANGELOG.md                  # DB changelog
│   ├── stamps/                       # disc stamp images (.webp)
│   └── scripts/
│       ├── scrape_pdga.py
│       ├── enrich.py
│       └── validate.py
├── docs/
│   └── superpowers/specs/
├── .github/workflows/
│   ├── auto-merge.yml                # claude/dev → main, paths-ignore data/** docs/**
│   ├── release.yml                   # app build, paths-ignore data/** docs/** *.md
│   └── data-validate.yml             # paths data/**, runs validate.py
├── CHANGELOG.md                      # app-facing
├── README.md
└── LICENSE                           # GPLv3
```

App fetches: `https://raw.githubusercontent.com/lightwraith8268/bagger/main/data/discs.json`

## 6. Screens / UX

### Bottom Nav (4 tabs)

1. **Shelf** — owned discs, filter by state/bag/brand/type. Default: state ∉ {Lost, Sold, Traded, Gifted}. Card grid (color circle + brand/mold + flight #s + state badge). FAB → camera (add disc).
2. **Bags** — bag list → bag detail (discs in bag, swipe remove, tap → disc detail). FAB → new bag.
3. **Discover** — full catalog DB browse, search by brand/mold/flight #, filter by speed/stability/type. Tap disc → detail w/ "Add to shelf" + "Add to wishlist".
4. **More** — Stats, Wishlist, Lost Map, Settings (nested).

### Add Disc Flow (camera FAB)

```
CameraX capture → ML Kit OCR (background)
  ├ confident match (top1 ≥ 0.85, gap ≥ 0.15)
  │   → Confirm screen: matched disc + flight #s, "This disc?" Y/N
  │       Y → Edit details (all optional) → Save
  │       N → Manual search (tokens prefilled)
  ├ multiple candidates (0.6 ≤ top1 < 0.85)
  │   → Pick screen: top 5 list w/ stamps + flight #s, tap to confirm or search
  └ no candidates (top1 < 0.6)
      → Manual search (extracted tokens prefilled in search box)
```

### Disc Detail (own disc)

- Photos carousel (front/back/stamp/other)
- Flight numbers chart (Compose Canvas, no third-party lib)
- All metadata (collapsed expander for power-user fields, beginners see lean view)
- State action buttons (Move to bag X / Mark lost / Mark sold / ...)
- State change → contextual sheet (e.g., Mark Lost → optional GPS pin + course + hole + notes)
- Edit · Delete

### Lost Disc Map (More → Lost Map)

- Google Maps Compose, pins per `LostDiscEvent` where `foundAt IS NULL`
- Tap pin → disc summary + "Mark found" + "Mark replaced"
- Filter chips: unfound · found · all
- Permission gate: location requested only on first "Mark Lost" tap (not at install)

### Stats (More → Stats)

- Total discs · total inventory value (sum purchasePrice)
- Count by type / brand / plastic (bar chart)
- Lost-this-year count
- Most-lost disc (max LostDiscEvent count per OwnedDisc)
- Brand distribution pie chart
- Retirement count

### Wishlist

- Same card grid as shelf
- Card → "Bought it" button → converts WishlistItem → OwnedDisc (prompts for actual purchase data)

### Disc Comparison

- Pick 2-3 from shelf or catalog
- Side-by-side flight #s + flight-chart overlay (Compose Canvas)

### Settings (per CLAUDE.md mandatory cards)

- In-app update card (Pipeline A — Play Core `AppUpdateManager`)
- Theme: Light / Dark / System (DataStore-backed)
- Backup → export JSON to Drive/local; Restore from JSON
- CSV export (insurance)
- Disc DB: last synced timestamp + "Refresh now"
- "Help improve disc ID" toggle (default OFF, gates `IdSubmissionQueue` upload phase 2)
- "Delete all my data" (wipes Room + DataStore + photo cache)
- Privacy / Terms / Feedback (mailto:info@inknironapps.com)
- What's New dialog (on update; shows all changes since last seen version)
- Version footer (`AppName v${BuildConfig.VERSION_NAME}`)

### Onboarding (first launch, empty shelf)

- Hero illustration + "Snap your first disc" CTA → camera flow

### What's New Dialog

- DataStore stores `lastSeenChangelogVersion`
- On launch, compare to `BuildConfig.VERSION_NAME`
- Different → parse `assets/CHANGELOG.md`, collect all version sections from `lastSeenChangelogVersion` (exclusive) up to current (inclusive), merge entries by category (Added/Changed/Fixed)
- Display single dialog grouped by category, version headers between groups
- On dismiss → write current version to DataStore
- Fresh install (`lastSeenChangelogVersion == null`) → skip dialog (onboarding covers it)

### Permissions

- **Camera** — request on first add-disc tap (rationale shown first if denied)
- **Location** — request on first "Mark Lost" action (rationale: pin location of lost disc)
- **Notifications** — deferred (no notifications phase 1)

## 7. Disc DB Pipeline

### Sync Logic

1. App launch → `DiscDbSyncWorker` (WorkManager expedited) runs if `lastSyncedAt < now - 7d`
2. GET `https://raw.githubusercontent.com/lightwraith8268/bagger/main/data/discs.json` w/ `If-None-Match: <stored-etag>`
3. 304 → bump `lastSyncedAt`, done
4. 200 → validate `schemaVersion` (bail if newer than app supports → banner "Update Bagger"), upsert all rows by id, store new etag
5. User force-refresh from Settings (bypasses 7d gate)

### Bundled Fallback

- APK ships `assets/discs-baseline.json` (frozen at build time via Gradle task copying `data/discs.json`)
- First launch w/o network → hydrate from baseline → app works offline immediately
- Remote fetch then overwrites/augments

### Image Strategy

- Stamps NOT bundled (size). URLs lazy-loaded via Coil w/ disk cache.
- Missing image → fallback flight-number badge (Compose-rendered, no asset)

### Community Contributions

- Open issue/PR on `lightwraith8268/bagger` repo touching `data/`
- `data-validate.yml` runs `scripts/validate.py` on PR
- Schema check, no duplicate ids, flight #s in plausible ranges, stamp URLs resolve 200
- Merge → next 7d sync pulls in
- No app update needed

### CI Path Separation

- `release.yml` triggers `paths-ignore: ['data/**', 'docs/**', '*.md']`
- `data-validate.yml` triggers `paths: ['data/**']`
- Data PRs don't trigger app builds

## 8. Photo-ID Pipeline

### Stages

```
[CameraX capture]
        ↓
[Preprocess]
   - downscale to 1024px max edge
   - rotate per EXIF
   - persist full-res to app private storage
        ↓
[ML Kit Text Recognition v2]
   - returns blocks + lines + bounding boxes + confidence
        ↓
[Token extraction]
   - flatten lines → uppercase tokens
   - strip noise (numbers alone, single chars, ®™)
   - keep tokens length ≥ 3
        ↓
[Match scoring against discs.json]
   For each catalog disc:
     - brandScore = max JaroWinkler(token, disc.brand) over tokens
     - moldScore  = max JaroWinkler(token, disc.mold OR alias) over tokens
     - combined   = 0.4*brandScore + 0.6*moldScore
   Sort desc, keep top 5 with combined ≥ 0.6
        ↓
[Decision]
   - top1 ≥ 0.85 AND (top1 - top2) ≥ 0.15  →  CONFIDENT (auto-confirm)
   - top1 ≥ 0.6                            →  CANDIDATES (pick top 5)
   - else                                  →  FALLBACK (manual search, tokens prefilled)
```

### JaroWinkler

- ~30 lines Kotlin in `ml/`, no external lib
- Tolerates char swaps, biased toward prefix match (good for OCR errors like `Destrover` ↔ `Destroyer`)

### Color Tiebreaker (deferred to phase 1.5 if useful)

- Sample center 100×100px region, dominant hue
- Stored as ID hint, used to filter ambiguous matches

### Performance Budget

- Capture → result screen: <2s on mid-range device
- Preprocess ~100ms · OCR ~300-800ms · matching pass over 2000 discs <50ms · UI rest

### Failure Modes

- No text detected → FALLBACK with empty prefill
- Camera permission denied → manual-add path, no degraded UX
- Disc DB not synced → matches against bundled baseline JSON
- Foreign-language stamps → tokens extracted but matches fail → FALLBACK

### Training Data Capture (deferred upload)

- Queue table `IdSubmissionQueue(photoPath, confirmedDiscId, ocrTokens, capturedAt)`
- Populated when user manually corrects after fallback
- Phase 1: holds locally only (no upload)
- Phase 2: backend exists → opted-in submissions upload
- Settings toggle "Help improve disc ID" gates this (default OFF)

## 9. Error Handling

### Principles

- Repo functions return `Result<T>` or throw mapped `BaggerError` sealed class
- ViewModels expose `UiState` sealed: `Loading | Success(data) | Error(message, retryAction?)`
- Snackbar for transient (network, OCR fail), full-screen error for fatal (DB corruption)

### Specific Failure Modes

| Scenario | Handling |
|---|---|
| Disc DB fetch fails (offline/404) | Use cached/baseline silently. Settings shows last sync timestamp red. |
| Disc DB schema version > app supports | Lock sync, banner "Update Bagger to get new discs." |
| Camera permission denied | Camera screen shows rationale + "Open Settings" + "Add manually" link |
| Location permission denied (Mark Lost) | Skip GPS, save event without lat/lng. Map shows as "no location" filter. |
| Photo storage full | Catch IOException, snackbar "Storage full, free up space" |
| Maps SDK unavailable (no Play Services) | Hide Lost Map nav entry, show list-only fallback |
| Backup restore on schema mismatch | Migration runner per schema version. Refuse if backup newer than app. |
| Corrupt Room DB | Auto-recover: rename to `bagger.db.corrupt-<ts>`, recreate, prompt restore from backup |
| OCR returns no text | FALLBACK to manual search, no error UI (expected case) |
| Wishlist→Owned conversion conflict | Disc already owned → ask "add as new copy?" Y/N |

## 10. Cloud-Ready Hooks (Phase 2 Prep)

- `Repository` interface in `domain/`. Phase 1 `LocalDiscRepository` impl. Phase 2 adds `SyncingDiscRepository` wrapping Local + Firestore. ViewModels unchanged.
- Entities have nullable `userId` + `syncedAt`. Null user = local-only.
- Backup JSON forward-compatible: `{schemaVersion, exportedAt, ownedDiscs[], bags[], lostEvents[], wishlist[]}`. Phase 2 cloud uses same shape.
- Sync conflict resolution: last-write-wins by `updatedAt` (no concurrent multi-device editing expected).

## 11. Privacy / GDPR Readiness

- All user data app-private storage, no SDK telemetry phase 1
- Settings → "Delete all my data" wipes Room + DataStore + photo cache
- "Help improve disc ID" toggle (default OFF) gates training-data submission queue
- Privacy policy URL: `https://lightwraith8268.github.io/inknironapps-legal/privacy-policy.html`
- Terms URL: `https://lightwraith8268.github.io/inknironapps-legal/terms.html`

## 12. Testing Strategy

### Unit (JUnit5 + MockK + Turbine) — bulk

- `domain/` use cases: target 90% coverage
- ML matcher (`JaroWinkler`, token extractor, scoring + decision): table-driven w/ real OCR samples
- Repos: fake DAOs verify upsert/query
- ViewModels: assert state transitions for all UiState branches

### Integration (androidTest, Robolectric for fast subset)

- Room DAOs: real in-memory DB, verify migrations + index queries
- Disc DB sync: mock OkHttp returning fixture JSON, verify upsert + etag + 304 short-circuit
- Backup/restore round-trip: export → wipe → import → assert all rows match

### UI (Compose UI Test, on emulator) — smoke flows only

1. Add disc via manual search → appears on Shelf
2. Mark disc lost → appears on Lost Map list
3. Move disc between bags → state updates
4. Backup → restore → data intact

ML pipeline tested via unit tests, NOT UI tests. Inject `FakeOcrEngine` returning canned tokens.

### Disc DB Validation (CI, Python)

- `data-validate.yml` on every PR touching `data/**`
- `scripts/validate.py`: schema conformance, no duplicate ids, flight #s plausible, every `primaryStampUrl` resolves 200

### Manual Verification Gate (per CLAUDE.md verification-before-completion)

- Cannot mark feature "done" without running app on real device + exercising golden path
- UI changes: before/after screenshots
- Permission flows: test grant + deny both

### Coverage Targets (guidance, not strict gates)

- domain/: 90%
- data/repos: 80%
- ui/viewmodels: 70%
- ui/composables: not measured (Compose preview + smoke tests instead)

## 13. Distribution

- **Flavor:** `playstore` only (Pipeline A from CLAUDE.md — Play Core update checker)
- No sideloaded flavor phase 1 (open source repo + Play Store reach sufficient)
- `applicationId`: `com.inknironapps.bagger`
- `versionName` auto-bumped via conventional commits (feat → minor, fix → patch, breaking → major)
- Release artifacts: APK (debug + release) + AAB (release, uploaded to Play Console)
- Signing: keystore Base64-encoded in GitHub Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
- Play Store deploy: service account JSON via `r0adkll/upload-google-play`

## 14. Phase Roadmap

| Phase | Scope |
|---|---|
| 1 (this spec) | Solo, local-only, OCR-only ID, manual fallback. Features: shelf, multi-bag mgmt, full disc lifecycle states (shelf/inbag/lost/found/sold/traded/retired/gifted), wishlist, stats, JSON backup/restore, lost-disc map, CSV inventory export, disc comparison. Excludes throw counter (UDisc overlap). |
| 2 | Auth (Firebase or Supabase), cloud sync, social-lite (friends see bags), training-data upload |
| 3 | Trained TFLite vision classifier (built from phase-2 collected dataset), full social (feed, reviews), UDisc CSV import, marketplace |

## 15. Open Items / Deferred

- Color-hue tiebreaker for ambiguous matches (phase 1.5 if observed needed)
- iOS / Flutter port (not committed)
- Marketplace / trade flow (phase 3)
- UDisc live integration (no public API — CSV import only feasible)

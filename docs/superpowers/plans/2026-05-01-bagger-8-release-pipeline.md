# Bagger Plan 8 — Release Pipeline Implementation Plan

> **For agentic workers:** Some tasks here REQUIRE user-side credentials (keystore, Play Console, Google Cloud). I can prepare configs/scripts but cannot execute the credentialed steps. User-gated tasks are clearly marked **🚪 USER ACTION**.

**Goal:** Wire the app for signed AAB release builds via GitHub Actions, ready for Play Store upload. All credentials externalized to env vars / CI secrets.

**Architecture:** Signing config in `app/build.gradle.kts` reads keystore from base64-encoded GH secret. `release.yml` decodes secret to file, builds + signs AAB, uploads as artifact (and optionally publishes via `r0adkll/upload-google-play` once Play Console linkage exists). Gradle properties layer for `MAPS_API_KEY` via env.

---

## Task 1: Signing Config in build.gradle.kts (autonomous)

Files:
- Modify: `app/build.gradle.kts`
- Create: `docs/SIGNING_GUIDE.md` (developer-facing setup doc, public-facing prose)

Add to `app/build.gradle.kts` above `android { }`:

```kotlin
val keystoreFile: java.io.File? = rootProject.file("release.keystore").takeIf { it.exists() }
val keystorePropsFile: java.io.File = rootProject.file("keystore.properties")
val keystoreProps: java.util.Properties = java.util.Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
```

Inside `android { ... }` add `signingConfigs`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = keystoreFile ?: rootProject.file("release.keystore.placeholder")
        storePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
        keyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS") ?: ""
        keyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD") ?: ""
    }
}
```

In `buildTypes { release { ... } }` add `signingConfig = signingConfigs.getByName("release")`.

Update `.gitignore`:
```
# Release signing artifacts (NEVER commit)
release.keystore
keystore.properties
*.jks
```

Commit: `chore: add release signing config (reads from keystore.properties or env)`

---

## Task 2: SIGNING_GUIDE.md (autonomous, public doc)

Create `docs/SIGNING_GUIDE.md`:

```markdown
# Signing & Release Setup

This guide describes how to generate a release keystore for Bagger and configure CI secrets so signed AABs can be built by GitHub Actions.

## 1. Generate a release keystore

Run once from the repository root. Pick a strong password and save it somewhere safe — losing it means losing the ability to publish updates to this app on the Play Store.

```bash
keytool -genkey -v -keystore release.keystore \
  -alias bagger-release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <STORE_PASSWORD> \
  -keypass <KEY_PASSWORD> \
  -dname "CN=Ink and Iron Apps, O=Ink and Iron Apps, C=US"
```

The file `release.keystore` is now in the repo root and is git-ignored.

## 2. Local builds

Create `keystore.properties` next to the keystore. Also git-ignored.

```properties
storePassword=<STORE_PASSWORD>
keyAlias=bagger-release
keyPassword=<KEY_PASSWORD>
```

`./gradlew :app:bundleRelease` will now produce a signed AAB at `app/build/outputs/bundle/release/app-release.aab`.

## 3. CI secrets

For the GitHub Actions release workflow to sign builds, add the following secrets to the repository (Settings → Secrets and variables → Actions):

- `KEYSTORE_BASE64` — output of `base64 -w 0 release.keystore` (Linux) or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` (PowerShell)
- `KEYSTORE_PASSWORD` — the same value as `storePassword` above
- `KEY_ALIAS` — `bagger-release`
- `KEY_PASSWORD` — the same value as `keyPassword` above
- `MAPS_API_KEY` — your Google Maps Android API key (from Google Cloud Console; restricted to your app's package name and SHA-1 of the release keystore)
- `PLAY_STORE_SERVICE_ACCOUNT_JSON` — JSON from a Google Play Console-linked service account (later, when Play Store auto-publish is wired)

After secrets are set, the release workflow will build a signed AAB on every push to `main`.

## 4. Map API key restrictions

In the Google Cloud Console, restrict the Maps API key to:

- **Application restriction:** Android apps
- **Package name:** `com.inknironapps.bagger`
- **SHA-1:** the SHA-1 fingerprint of `release.keystore` (`keytool -list -v -keystore release.keystore | grep SHA1`)

This prevents the key from being abused if it leaks.

## 5. Privacy policy and terms

Both URLs are already published at:

- https://lightwraith8268.github.io/inknironapps-legal/privacy-policy.html
- https://lightwraith8268.github.io/inknironapps-legal/terms.html

These are linked from the Settings screen and will be linked again from the Play Store listing.

## 6. Initial Play Store submission

The first submission must be done by hand through the Play Console:

1. Create a new app in the Play Console.
2. Upload the signed AAB from `app/build/outputs/bundle/release/app-release.aab`.
3. Fill out the store listing (title, short description, full description, screenshots, feature graphic).
4. Configure data safety and privacy form.
5. Submit to the internal testing track first; promote through closed testing → open testing → production.

Subsequent releases can be automated via the `r0adkll/upload-google-play` GitHub Action once a service account with Play Console permissions is linked.
```

Commit: `docs: add signing + release setup guide`

---

## Task 3: Update release.yml to Build Signed AAB (autonomous)

Modify `.github/workflows/release.yml`. Adds keystore decode step + signed AAB build. Falls back gracefully when secrets are absent (CI still produces an unsigned debug APK so the workflow doesn't break before Task 4 runs).

```yaml
name: Release Build

on:
  push:
    branches: [main]
    paths-ignore:
      - 'data/**'
      - 'docs/**'
      - '*.md'
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }

      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }

      - uses: gradle/actions/setup-gradle@v4
        with: { cache-read-only: false }

      - name: Decode keystore
        if: env.KEYSTORE_BASE64 != ''
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 -d > release.keystore
          ls -la release.keystore

      - name: Run unit tests
        run: ./gradlew :app:testPlaystoreDebugUnitTest

      - name: Lint
        run: ./gradlew :app:lintPlaystoreDebug

      - name: Build debug APK
        run: ./gradlew :app:assemblePlaystoreDebug

      - name: Build release AAB (signed if secrets present)
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          ORG_GRADLE_PROJECT_MAPS_API_KEY: ${{ secrets.MAPS_API_KEY }}
        run: |
          if [ -f release.keystore ]; then
            ./gradlew :app:bundlePlaystoreRelease
          else
            echo "::warning::No keystore configured — skipping release AAB."
          fi

      - name: Upload debug APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: bagger-playstore-debug-${{ github.sha }}
          path: app/build/outputs/apk/playstore/debug/*.apk
          retention-days: 14

      - name: Upload release AAB artifact
        if: hashFiles('app/build/outputs/bundle/playstoreRelease/*.aab') != ''
        uses: actions/upload-artifact@v4
        with:
          name: bagger-playstore-release-${{ github.sha }}
          path: app/build/outputs/bundle/playstoreRelease/*.aab
          retention-days: 90
```

Commit: `ci: build signed AAB in release.yml when keystore secrets present`

---

## Task 4: Play Store Listing Asset Directory + Template (autonomous)

Create `docs/play-store/`:

```
docs/play-store/
├── README.md
├── description-short.txt
├── description-full.txt
├── data-safety.md
└── screenshots/
    └── README.md
```

```markdown
<!-- docs/play-store/README.md -->
# Play Store Listing Assets

Files in this directory are prepared for the Play Console listing. They are not bundled with the app.

## Required for first submission

- **App title:** Bagger
- **Short description (80 char limit):** see `description-short.txt`
- **Full description (4000 char limit):** see `description-full.txt`
- **App icon (512×512 PNG):** generated from the launcher icon — TODO before first submission
- **Feature graphic (1024×500):** TODO before first submission
- **Phone screenshots (min 2, max 8, 16:9 or 9:16):** capture from emulator and place in `screenshots/`
- **Data safety form:** see `data-safety.md`
- **Content rating:** complete IARC questionnaire in Play Console
- **Target audience:** 13+
- **Privacy policy URL:** `https://lightwraith8268.github.io/inknironapps-legal/privacy-policy.html`
```

```text
<!-- docs/play-store/description-short.txt -->
Catalog your disc-golf discs with on-device photo identification.
```

```text
<!-- docs/play-store/description-full.txt -->
Bagger turns your phone into a smart inventory for your disc-golf bag.

Snap a photo of any disc and Bagger identifies it from a catalog of popular molds, capturing the brand, mold, and flight numbers automatically. Don't recognize the disc? Search the catalog manually — your fallback always works.

Features:

- Photo identification powered by on-device machine learning. No cloud uploads, no accounts, no telemetry.
- Multiple bags. Organize discs into named bags ("Tournament", "Wooded course", "Beginner loaner") and move them around as your loadout changes.
- Full lifecycle tracking. Mark discs as in your bag, on your shelf, lost, found, sold, traded, retired, or gifted.
- Lost disc map. Pin lost discs on a map with optional GPS, course name, and hole number — see at a glance where you've lost what.
- Wishlist. Add discs you want to buy and convert them to owned discs in one tap when you pick them up.
- Compare flights side by side. Pick two or three discs and overlay their projected flight paths on the same chart.
- Stats. Inventory totals, brand and disc-type breakdowns, lost-this-year count, retired count.
- Local backup. Export your entire collection to JSON or CSV with the system file picker. Restore from JSON on a new device.
- Material 3 design with light, dark, and system theme support.

Bagger is open source under the GNU GPLv3 and built by Ink and Iron Apps.

Privacy: All data stays on your device unless you manually export a backup.
```

```markdown
<!-- docs/play-store/data-safety.md -->
# Data Safety Form Answers

For the Play Console data safety questionnaire.

## Data collected
- **Personal info:** None
- **Financial info:** None (purchase price stored locally only, optional, never transmitted)
- **Health info:** None
- **Messages:** None
- **Photos and videos:** Yes — photos of user's discs, used for identification, stored locally on device, NEVER uploaded
- **Audio:** None
- **Files and docs:** None (backup files written by user via system picker; not auto-uploaded)
- **Calendar / contacts:** None
- **Location:** Yes (optional, only when user marks a disc lost) — used for the Lost discs map; stored locally only, NEVER uploaded
- **App activity:** None
- **Web history:** None
- **App info & performance:** None (no crash reporting, no analytics)
- **Device or other IDs:** None

## Encryption in transit
N/A — no network transfer of user data.

## Data deletion
The Settings screen has a "Delete all my data" action that wipes the local database, preferences, and cached photos.

## Sharing with third parties
None.
```

```markdown
<!-- docs/play-store/screenshots/README.md -->
# Screenshots

Capture from emulator at the standard Play Store screen sizes:

- Phone: 16:9 ratio, 1080×1920 (portrait) or 1920×1080 (landscape)
- 7" tablet: 1200×1920
- 10" tablet: 1600×2560

Capture command:

```bash
adb exec-out screencap -p > docs/play-store/screenshots/01-shelf.png
```

Suggested screens (5–8 total):
1. Shelf with sample discs
2. Discover with catalog browse
3. Camera capture / disc ID confirmation
4. Disc detail with state controls
5. Lost discs map
6. Stats screen
7. Settings
8. What's New dialog (optional)
```

Commit: `docs: add Play Store listing assets + data safety + descriptions`

---

## Task 5: Final Push + Tag CHANGELOG v1.0.0-rc.1 (autonomous)

Update `app/build.gradle.kts` `versionName` from `"0.1.0"` to `"1.0.0-rc.1"` and `versionCode` from `1` to `2`. (Beta-track candidate.)

Append to `CHANGELOG.md`:

```markdown
## [1.0.0-rc.1] - 2026-05-01

### Added

- Release signing pipeline. The release build type now signs with a keystore loaded from either a local `keystore.properties` file or CI environment variables. The GitHub Actions release workflow decodes a base64-encoded keystore secret on every main-branch push and produces a signed AAB artifact.
- Signing setup guide at `docs/SIGNING_GUIDE.md` covering keystore generation, local builds, CI secrets, Maps API key restriction, and first Play Store submission.
- Play Store listing assets (`docs/play-store/`) — short and full descriptions, data safety questionnaire answers, and a screenshots placeholder directory.
- App version bumped to 1.0.0-rc.1 ahead of the first internal-testing track submission.
```

Commit: `docs: tag 1.0.0-rc.1 — Plan 8 release pipeline complete`

Push, verify CI green.

---

## 🚪 USER ACTIONS REQUIRED (cannot automate)

These steps must be done by the user with their own credentials. Detailed instructions are in `docs/SIGNING_GUIDE.md`. Once complete, the next push to `main` produces a signed AAB artifact ready for upload.

### A. Generate keystore

```bash
keytool -genkey -v -keystore release.keystore \
  -alias bagger-release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Ink and Iron Apps, O=Ink and Iron Apps, C=US"
```

Save the password.

### B. Push 4 secrets to GitHub repo

```bash
base64 -w 0 release.keystore | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD                # paste password
gh secret set KEY_ALIAS                        # paste "bagger-release"
gh secret set KEY_PASSWORD                     # paste key password
```

### C. Get a Maps API key

1. Open https://console.cloud.google.com
2. Create a new project (or reuse existing)
3. Enable "Maps SDK for Android"
4. Create API credentials → API key
5. Restrict to package `com.inknironapps.bagger` + SHA-1 of `release.keystore` (`keytool -list -v -keystore release.keystore | grep SHA1`)
6. `gh secret set MAPS_API_KEY` → paste the key

### D. Create Play Console app

1. Open https://play.google.com/console
2. Create app: name=Bagger, default language=English (United States), app type=App, free
3. Complete declarations (Designed for Families: No, Government app: No, etc.)

### E. First AAB upload

1. Trigger release workflow: `gh workflow run release.yml --ref main`
2. Download AAB artifact: `gh run download --name bagger-playstore-release-<sha>`
3. Upload `app-release.aab` to Play Console → Internal testing → Create new release

### F. Play Console: link service account for auto-publish (optional, later)

1. Play Console → Setup → API access → link Google Cloud project
2. Create service account in Cloud Console with **Service Account User** role
3. Grant Play Console access (Release manager role)
4. Download JSON key
5. `gh secret set PLAY_STORE_SERVICE_ACCOUNT_JSON < service-account.json`
6. Add `r0adkll/upload-google-play` step to `release.yml`

---

## Verification (autonomous part)

- [ ] All 5 task commits land
- [ ] Build green (debug APK still produced; AAB skipped if no keystore)
- [ ] Lint clean
- [ ] CI auto-merge + release.yml green
- [ ] CHANGELOG v1.0.0-rc.1 tagged

After USER ACTIONS A–E complete, the next push to main produces a signed AAB artifact ready for Play Console upload.

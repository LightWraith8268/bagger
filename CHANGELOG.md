# Changelog

All notable changes to Bagger are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] - 2026-05-01

### Added

- Wishlist with one-tap add from any catalog disc detail page and a Bought it action that converts a wishlist item into an owned disc on your shelf.
- Lost disc tracking: marking a disc Lost from the disc detail screen now opens a dialog for course name, hole number, optional notes, and an opt-in GPS pin. The Lost discs map under More renders pinned events on Google Maps; events without GPS appear in a list below.
- Disc comparison screen for picking two or three discs side by side with a flight-chart Canvas overlay and flight numbers per disc.
- More tab is now a real menu wiring Wishlist, Lost discs map, Compare discs, plus placeholder rows for Stats and Settings (filled in by Plan 7).
- New repository layer for the wishlist and lost-disc-event tables; LocationProvider wraps FusedLocationProviderClient.

## [0.5.0] - 2026-05-01

### Added

- Photo-based disc identification flow. Tap Add disc to open the camera, snap a photo of the disc, and Bagger uses on-device ML Kit text recognition with a Jaro–Winkler matcher to identify the disc against its catalog. Confident matches go straight to a confirmation screen; ambiguous matches present up to five candidates; unrecognized photos fall back to a manual catalog search prefilled with detected text.
- Disc details form for plastic type, weight, color, condition, and notes — used by both the photo-ID flow and the catalog Add to my shelf action.
- Local photo storage in app-private directory plus a FileProvider for sharing.
- Training-data submission queue: when the photo-ID falls back to manual search and the user picks a disc, the photo plus extracted tokens plus confirmed disc id are saved locally, ready for an opt-in cloud upload in a later phase.
- Unit tests for the matcher (Jaro–Winkler, token extractor, decision engine).

## [0.4.0] - 2026-05-01

### Added

- Real Shelf screen with filterable owned-disc grid and an Add disc shortcut.
- Bags tab with a list of saved bags, a create dialog, and a bag detail screen showing the discs assigned to that bag.
- Discover screen for browsing the full disc catalog with text search and disc-type filters.
- Disc detail screens for both catalog discs (with an Add to my shelf action) and owned discs (with state controls and bag reassignment).
- Repository layer (`DiscCatalogRepository`, `OwnedDiscRepository`, `BagRepository`) backed by Room, wired through Hilt.
- New shared UI components: `DiscCard`, `FlightNumbersRow`, and `EmptyState`.
- Compose smoke tests covering empty shelf, Discover search field, and Bags create action.

## [0.3.0] - 2026-05-01

### Added

- App-side remote disc database sync. On launch, the app fetches the canonical `discs.json` from the public repository, applies it to the local Room cache, and stores the response ETag for cheap revalidation. Subsequent syncs run on a 7-day periodic WorkManager schedule with network constraints.
- Schema version handshake: if the remote catalog declares a newer schema than the installed app understands, sync stops gracefully without corrupting local data.
- Test suite for the sync worker using MockWebServer covering successful upsert, 304-not-modified short-circuit, and unsupported-schema failure paths.

## [0.2.0] - 2026-05-01

### Added

- Disc catalog data pipeline: Python tooling under `data/scripts/` covering a PDGA approved-disc scraper, a shared manufacturer-scraper framework, and per-brand scrapers for Innova, Discraft, MVP, Dynamic Discs, Latitude 64, Discmania, Prodigy, and Westside. Each per-brand scraper currently uses fixture-based parsers; live-site iteration is tracked as future work.
- JSON Schema (`data/schema.json`) describing the disc catalog format, validated in CI.
- Initial canonical disc database at `data/discs.json` containing 57 popular discs across all 8 tracked manufacturers, hand-curated from public flight-number disclosures.
- Continuous integration: the `data-validate` workflow now runs the full Python test suite and `validate.py --no-net` on every pull request that touches `data/`.
- The bundled disc fixture inside the APK, used on first launch before any remote sync, now contains thirty representative discs spanning all five disc types and all eight brands.

### Changed

- The instrumented `BaselineDiscLoaderTest` now asserts at least ten baseline discs are present rather than exactly ten, allowing the bundled fixture to grow over time without breaking the test.

## [0.1.0] - 2026-04-30

### Added

- Initial project scaffolding: Kotlin/Compose application with Hilt dependency injection and Room database.
- Material 3 theme using the Ink & Iron Apps palette and typography system.
- Bottom navigation with placeholder Shelf, Bags, Discover, and More tabs.
- DataStore preferences wrapper for theme mode, onboarding state, and disc database sync metadata.
- Bundled fixture of ten common discs to support development and offline first launch.
- Splash screen using the Android 12+ SplashScreen API.
- Continuous integration: auto-merge workflow for `claude/dev` to `main`, release build workflow producing debug APK artifacts, and a placeholder disc database validation workflow.

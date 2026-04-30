# Bagger

Open-source disc-golf disc cataloging app for Android. Snap a photo of a disc and Bagger identifies it from on-device OCR, stores it on your shelf with flight numbers, and helps you organize multiple bags. Track lost discs on a map, maintain a wishlist, view stats, and export your inventory.

**Status:** alpha — active development. Phase 1 (solo, local-only) underway.

## Features (Phase 1)

- Photo-based disc identification using on-device ML Kit OCR. No cloud, no accounts, no telemetry.
- Catalog of public disc database, sourced from the PDGA approved disc list and manufacturer flight numbers.
- Multiple bags per user; move discs between bags freely.
- Full disc lifecycle states: shelf, in bag, lost, found, sold, traded, retired, gifted.
- Lost disc map with optional GPS pins and course/hole notes.
- Wishlist with one-tap conversion to owned disc when purchased.
- Inventory stats and CSV export.
- Local JSON backup and restore — no account required.
- Material 3 design with light, dark, and system theme support.

## Roadmap

| Phase | Scope |
|---|---|
| 1 | Solo, local-only. OCR-based disc ID with manual fallback. All features above. |
| 2 | Optional accounts, cloud sync, social-lite (friends see each other's bags), training data uploads to improve ID accuracy. |
| 3 | Trained vision classifier (built from phase-2 collected dataset), full social feed, disc reviews, UDisc CSV import, marketplace. |

## Disc Database

Disc data lives in [`data/discs.json`](data/discs.json) within this repository. Community contributions welcome — open a pull request adding new discs or correcting flight numbers. CI validates the JSON schema automatically on every PR touching `data/`.

The bundled APK ships with a baseline copy of `discs.json` so the app works offline on first launch. Updates are pulled in the background once a week (or on demand from Settings).

## Building from Source

Requirements:

- Android Studio Ladybug or newer (or just the Android command-line tools)
- JDK 21
- Android SDK 35

```bash
git clone https://github.com/LightWraith8268/bagger.git
cd bagger
./gradlew :app:installPlaystoreDebug
```

Run on a connected device or emulator running Android 12 (API 31) or newer.

## Project Layout

```
bagger/
├── app/                     # Android application module (Kotlin/Compose)
├── data/                    # Public disc database (JSON + scripts)
├── docs/superpowers/        # Design specs and implementation plans
├── .github/workflows/       # CI: auto-merge, release builds, data validation
├── CHANGELOG.md
├── LICENSE                  # GPLv3
└── README.md
```

## Contributing

Issues and pull requests are welcome.

- **App code changes:** open a PR against `main`. CI runs unit tests and lint on every PR.
- **Disc database additions or corrections:** edit `data/discs.json` and open a PR. The `data-validate` workflow confirms the JSON parses, schema matches, and no IDs duplicate before review.

A `CONTRIBUTING.md` with full guidelines is on the way.

## License

Bagger is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE) for the full text. Forks and derivative works must remain open source under the same terms.

## Brand

Bagger is published by **Ink & Iron Apps**. Visit [inknironapps.com](https://inknironapps.com) for our other titles.

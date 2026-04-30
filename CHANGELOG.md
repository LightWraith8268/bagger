# Changelog

All notable changes to Bagger are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-04-30

### Added

- Initial project scaffolding: Kotlin/Compose application with Hilt dependency injection and Room database.
- Material 3 theme using the Ink & Iron Apps palette and typography system.
- Bottom navigation with placeholder Shelf, Bags, Discover, and More tabs.
- DataStore preferences wrapper for theme mode, onboarding state, and disc database sync metadata.
- Bundled fixture of ten common discs to support development and offline first launch.
- Splash screen using the Android 12+ SplashScreen API.
- Continuous integration: auto-merge workflow for `claude/dev` to `main`, release build workflow producing debug APK artifacts, and a placeholder disc database validation workflow.

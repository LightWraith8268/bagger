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

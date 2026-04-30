# Bagger Plan 1 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up Android project scaffolding — Kotlin/Compose app w/ Hilt, Room, Compose Navigation, M3 theme, DataStore, CI workflows, GPLv3 license, test infra. Output: buildable APK that launches to bottom-nav shell w/ 4 placeholder tabs, persists prefs, runs Room migrations, has CI green.

**Architecture:** Single Gradle module (`app/`) phase 1, ready to split into multi-module later. MVVM + Repository pattern. All entities Room-only this plan; sync logic deferred to Plan 2. Compose Navigation w/ bottom bar. Hilt for DI. Pragmatic TDD — unit tests for pure logic, instrumented tests for Room DAOs, smoke UI test for nav.

**Tech Stack:** Kotlin 2.1.0 · Jetpack Compose (Material 3) · Hilt 2.52 · Room 2.7.0 · DataStore Preferences · Coroutines 1.10 · JUnit 5 + MockK + Turbine · Gradle 8.10 (Kotlin DSL + version catalog) · Min SDK 31 · Target SDK 35 · JDK 21

---

## File Structure

```
bagger/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   └── discs-baseline.json          # 10-disc dev fixture
│       │   ├── java/com/inknironapps/bagger/
│       │   │   ├── BaggerApp.kt                 # @HiltAndroidApp
│       │   │   ├── MainActivity.kt              # Compose entry + splash
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   │   ├── BaggerDatabase.kt
│       │   │   │   │   ├── Converters.kt
│       │   │   │   │   ├── entity/             # all @Entity classes
│       │   │   │   │   │   ├── DiscEntity.kt
│       │   │   │   │   │   ├── OwnedDiscEntity.kt
│       │   │   │   │   │   ├── OwnedDiscPhotoEntity.kt
│       │   │   │   │   │   ├── BagEntity.kt
│       │   │   │   │   │   ├── LostDiscEventEntity.kt
│       │   │   │   │   │   ├── WishlistItemEntity.kt
│       │   │   │   │   │   ├── DiscDbMetaEntity.kt
│       │   │   │   │   │   └── IdSubmissionQueueEntity.kt
│       │   │   │   │   └── dao/                # all DAOs (one per entity group)
│       │   │   │   │       ├── DiscDao.kt
│       │   │   │   │       ├── OwnedDiscDao.kt
│       │   │   │   │       ├── BagDao.kt
│       │   │   │   │       ├── LostDiscEventDao.kt
│       │   │   │   │       ├── WishlistDao.kt
│       │   │   │   │       └── DiscDbMetaDao.kt
│       │   │   │   └── prefs/
│       │   │   │       └── BaggerPrefs.kt       # DataStore wrapper
│       │   │   ├── di/
│       │   │   │   ├── DatabaseModule.kt
│       │   │   │   └── PrefsModule.kt
│       │   │   └── ui/
│       │   │       ├── theme/
│       │   │       │   ├── Color.kt
│       │   │       │   ├── Theme.kt
│       │   │       │   └── Type.kt
│       │   │       └── nav/
│       │   │           ├── BaggerNavHost.kt
│       │   │           ├── BottomNav.kt
│       │   │           └── Destinations.kt
│       │   └── res/
│       │       ├── values/strings.xml
│       │       ├── values/themes.xml            # SplashScreen API theme
│       │       ├── drawable/ic_launcher_*.xml
│       │       └── mipmap-anydpi-v26/
│       ├── test/                                # JVM unit tests
│       └── androidTest/                         # instrumented tests
├── gradle/
│   ├── libs.versions.toml                       # version catalog
│   └── wrapper/
├── settings.gradle.kts
├── build.gradle.kts                             # root
├── gradle.properties
├── gradlew / gradlew.bat
├── data/                                        # Plan 2 fills this
│   └── .gitkeep
├── docs/
│   ├── superpowers/specs/2026-04-28-bagger-design.md   # exists
│   └── superpowers/plans/                       # this plan + future
├── .github/workflows/
│   ├── auto-merge.yml
│   ├── release.yml
│   └── data-validate.yml
├── CHANGELOG.md
├── README.md
└── LICENSE                                      # GPLv3 full text
```

---

## Task 1: Gradle Wrapper + Version Catalog

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` (via `gradle wrapper`)

- [ ] **Step 1: Install Gradle wrapper at version 8.10**

```bash
# from D:\Coding\bagger; uses globally available Gradle? No — none installed.
# Use Android Studio "New Project" generator OR bootstrap via JDK + curl.
# Simplest: download distribution wrapper script.
curl -L -o /tmp/gradle-8.10-bin.zip https://services.gradle.org/distributions/gradle-8.10-bin.zip
unzip -q /tmp/gradle-8.10-bin.zip -d /tmp/
/tmp/gradle-8.10/bin/gradle wrapper --gradle-version 8.10 --distribution-type bin
```

Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` + `.properties`.

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "bagger"
include(":app")
```

- [ ] **Step 3: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.0"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
hilt = "2.52"
hilt-compose = "1.2.0"
compose-bom = "2024.12.01"
core-ktx = "1.15.0"
lifecycle = "2.8.7"
activity-compose = "1.9.3"
nav-compose = "2.8.5"
room = "2.7.0"
datastore = "1.1.1"
coroutines = "1.10.1"
splashscreen = "1.0.1"
junit-jupiter = "5.11.4"
mockk = "1.13.13"
turbine = "1.2.0"
robolectric = "4.14"
androidx-test-junit = "1.2.1"
espresso = "3.6.1"

[libraries]
core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "splashscreen" }

compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
nav-compose = { module = "androidx.navigation:navigation-compose", version.ref = "nav-compose" }
hilt-nav-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hilt-compose" }

hilt = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }

room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }

datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }

junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit-jupiter" }
junit-jupiter-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit-jupiter" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-test-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-junit" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 4: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 5: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
android.defaults.buildfeatures.buildconfig=true
kotlin.code.style=official
ksp.useKSP2=true
```

- [ ] **Step 6: Verify wrapper works**

Run: `./gradlew --version`
Expected: `Gradle 8.10` + `Kotlin: ...` + `Launcher JVM: 21.x`.

- [ ] **Step 7: Commit**

```bash
git add gradlew gradlew.bat gradle/ settings.gradle.kts build.gradle.kts gradle.properties
git commit -m "chore: bootstrap Gradle wrapper 8.10 + version catalog"
```

---

## Task 2: App Module Build Script

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/inknironapps/bagger/BaggerApp.kt`

- [ ] **Step 1: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.inknironapps.bagger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inknironapps.bagger"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("playstore") {
            dimension = "distribution"
            buildConfigField("Boolean", "ENABLE_UPDATE_CHECKER", "false")
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.nav.compose)
    implementation(libs.hilt.nav.compose)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 2: Write `app/proguard-rules.pro`**

```proguard
# Hilt
-keepclasseswithmembernames class * { @dagger.hilt.android.* <init>(...); }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Compose
-keep class androidx.compose.runtime.** { *; }

# Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
```

- [ ] **Step 3: Write `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:name=".BaggerApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Bagger.Splash"
        tools:targetApi="35">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Bagger.Splash">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Write `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">Bagger</string>
    <string name="nav_shelf">Shelf</string>
    <string name="nav_bags">Bags</string>
    <string name="nav_discover">Discover</string>
    <string name="nav_more">More</string>
</resources>
```

- [ ] **Step 5: Write `app/src/main/res/values/themes.xml`**

```xml
<resources>
    <style name="Theme.Bagger.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#0F1115</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_foreground</item>
        <item name="postSplashScreenTheme">@style/Theme.Bagger</item>
    </style>
    <style name="Theme.Bagger" parent="android:Theme.Material.NoActionBar" />
</resources>
```

- [ ] **Step 6: Create empty backup config files**

```xml
<!-- app/src/main/res/xml/backup_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content />
```

```xml
<!-- app/src/main/res/xml/data_extraction_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup />
    <device-transfer />
</data-extraction-rules>
```

- [ ] **Step 7: Write `BaggerApp.kt`**

```kotlin
package com.inknironapps.bagger

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BaggerApp : Application()
```

- [ ] **Step 8: Add minimal placeholder launcher icons (final art lands in polish plan)**

Create files (copy verbatim — these compile and render a teal solid square as the launcher icon, plenty good for Plan 1):

```xml
<!-- app/src/main/res/values/ic_launcher_background.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#095F73</color>
</resources>
```

```xml
<!-- app/src/main/res/drawable/ic_launcher_foreground.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#E8E4DA"
        android:pathData="M54,30 m-18,0 a18,18 0 1,0 36,0 a18,18 0 1,0 -36,0z"/>
</vector>
```

```xml
<!-- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml -->
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

```xml
<!-- app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml -->
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

Also add legacy fallback for pre-API-26 devices (Min SDK 31 means we don't strictly need them, but Manifest still references the round variant). Create one PNG-free fallback:

```xml
<!-- app/src/main/res/mipmap/ic_launcher.xml (NOTE: this lives in mipmap/ directly, not mipmap-anydpi-v26) -->
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

(Same for `mipmap/ic_launcher_round.xml`.)

Real branded icon w/ I&I monogram lands in the final polish plan; this stub unblocks builds.

- [ ] **Step 9: Verify build compiles (no source files yet beyond Application)**

Run: `./gradlew :app:assemblePlaystoreDebug --offline=false`
Expected: BUILD SUCCESSFUL with one APK at `app/build/outputs/apk/playstore/debug/app-playstore-debug.apk`.

- [ ] **Step 10: Commit**

```bash
git add app/
git commit -m "chore: scaffold app module + manifest + Hilt application"
```

---

## Task 3: Material 3 Theme + Type System

**Files:**
- Create: `app/src/main/java/com/inknironapps/bagger/ui/theme/Color.kt`
- Create: `app/src/main/java/com/inknironapps/bagger/ui/theme/Type.kt`
- Create: `app/src/main/java/com/inknironapps/bagger/ui/theme/Theme.kt`

- [ ] **Step 1: Write `Color.kt` matching Ink & Iron palette**

```kotlin
package com.inknironapps.bagger.ui.theme

import androidx.compose.ui.graphics.Color

val InkBackground = Color(0xFF0F1115)
val InkPaper      = Color(0xFFE8E4DA)
val InkTeal       = Color(0xFF095F73)
val InkTealBright = Color(0xFF3BB3C9)
val InkMuted      = Color(0xFF9CA3AF)
```

- [ ] **Step 2: Write `Type.kt`**

```kotlin
package com.inknironapps.bagger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DisplayFamily = FontFamily.Serif      // EB Garamond shipped in later plan
private val UiFamily      = FontFamily.SansSerif  // Inter shipped in later plan

val BaggerTypography = Typography(
    displayLarge  = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 48.sp),
    titleLarge    = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp),
    bodyLarge     = TextStyle(fontFamily = UiFamily,      fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium    = TextStyle(fontFamily = UiFamily,      fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge    = TextStyle(fontFamily = UiFamily,      fontWeight = FontWeight.Medium, fontSize = 14.sp)
)
```

- [ ] **Step 3: Write `Theme.kt`**

```kotlin
package com.inknironapps.bagger.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary       = InkTeal,
    onPrimary     = InkPaper,
    secondary     = InkTealBright,
    background    = InkBackground,
    onBackground  = InkPaper,
    surface       = InkBackground,
    onSurface     = InkPaper,
    surfaceVariant = Color(0xFF1A1F26),
    onSurfaceVariant = InkMuted
)

private val LightColors = lightColorScheme(
    primary       = InkTeal,
    onPrimary     = InkPaper,
    secondary     = InkTealBright,
    background    = InkPaper,
    onBackground  = InkBackground,
    surface       = InkPaper,
    onSurface     = InkBackground,
    surfaceVariant = Color(0xFFD7D2C5),
    onSurfaceVariant = Color(0xFF4B5563)
)

@Composable
fun BaggerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = BaggerTypography, content = content)
}
```

Note: missing import `import androidx.compose.ui.graphics.Color` — add to top of `Theme.kt`.

- [ ] **Step 4: Add missing import**

Add `import androidx.compose.ui.graphics.Color` after package line.

- [ ] **Step 5: Build to confirm theme compiles**

Run: `./gradlew :app:compilePlaystoreDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/inknironapps/bagger/ui/theme/
git commit -m "feat: add Ink & Iron M3 theme + typography"
```

---

## Task 4: Room Entities + DAOs (No Logic Yet)

**Files:**
- Create: `app/src/main/java/com/inknironapps/bagger/data/db/Converters.kt`
- Create: 8 entity files under `data/db/entity/`
- Create: 6 DAO files under `data/db/dao/`
- Create: `app/src/main/java/com/inknironapps/bagger/data/db/BaggerDatabase.kt`
- Test: `app/src/androidTest/java/com/inknironapps/bagger/data/db/BaggerDatabaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/androidTest/java/com/inknironapps/bagger/data/db/BaggerDatabaseTest.kt
package com.inknironapps.bagger.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inknironapps.bagger.data.db.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class BaggerDatabaseTest {
    private lateinit var db: BaggerDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BaggerDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun insertAndQueryDisc() = runBlocking {
        val disc = DiscEntity(
            id = "innova-destroyer", brand = "Innova", mold = "Destroyer",
            speed = 12f, glide = 5f, turn = -1f, fade = 3f,
            discType = "Driver", stability = "overstable",
            pdgaApproved = true, yearReleased = 2008, primaryStampUrl = null
        )
        db.discDao().upsertAll(listOf(disc))
        val out = db.discDao().getById("innova-destroyer")
        assertEquals("Destroyer", out?.mold)
    }

    @Test fun insertOwnedDiscWithDiscFK() = runBlocking {
        val disc = DiscEntity("innova-aviar", "Innova", "Aviar", 2f, 3f, 0f, 1f, "Putter", "stable", true, 1985, null)
        db.discDao().upsertAll(listOf(disc))
        val owned = OwnedDiscEntity(
            id = "uuid-1", discId = "innova-aviar", plasticType = "DX",
            weight = 175, color = "#ffffff", condition = "New", state = "Shelf",
            bagId = null, purchaseDate = null, purchasePrice = null, notes = null,
            isOriginalOwner = true, customTags = emptyList(),
            createdAt = 1L, updatedAt = 1L, userId = null, syncedAt = null
        )
        db.ownedDiscDao().upsert(owned)
        val all = db.ownedDiscDao().getAll()
        assertEquals(1, all.size)
        assertEquals("uuid-1", all.first().id)
    }
}
```

- [ ] **Step 2: Run test, verify FAIL**

Run: `./gradlew :app:connectedPlaystoreDebugAndroidTest --tests com.inknironapps.bagger.data.db.BaggerDatabaseTest`
Expected: compile error (entities not yet defined).

- [ ] **Step 3: Write `Converters.kt`**

```kotlin
package com.inknironapps.bagger.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromStringList(value: List<String>?): String? = value?.joinToString("")
    @TypeConverter fun toStringList(value: String?): List<String> =
        value?.takeIf { it.isNotEmpty() }?.split("") ?: emptyList()
}
```

- [ ] **Step 4: Write `DiscEntity.kt`**

```kotlin
package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discs")
data class DiscEntity(
    @PrimaryKey val id: String,
    val brand: String,
    val mold: String,
    val speed: Float,
    val glide: Float,
    val turn: Float,
    val fade: Float,
    val discType: String,
    val stability: String,
    val pdgaApproved: Boolean,
    val yearReleased: Int?,
    val primaryStampUrl: String?
)
```

- [ ] **Step 5: Write `OwnedDiscEntity.kt`**

```kotlin
package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "owned_discs",
    foreignKeys = [
        ForeignKey(entity = DiscEntity::class, parentColumns = ["id"], childColumns = ["discId"]),
        ForeignKey(entity = BagEntity::class,  parentColumns = ["id"], childColumns = ["bagId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("discId"), Index("state", "bagId")]
)
data class OwnedDiscEntity(
    @PrimaryKey val id: String,
    val discId: String,
    val plasticType: String?,
    val weight: Int?,
    val color: String?,
    val condition: String,
    val state: String,
    val bagId: String?,
    val purchaseDate: Long?,
    val purchasePrice: Long?,    // cents
    val notes: String?,
    val isOriginalOwner: Boolean,
    val customTags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String?,
    val syncedAt: Long?
)
```

- [ ] **Step 6: Write `BagEntity.kt`**

```kotlin
package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bags")
data class BagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val iconColor: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String?,
    val syncedAt: Long?
)
```

- [ ] **Step 7: Write `OwnedDiscPhotoEntity.kt`**

```kotlin
package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "owned_disc_photos",
    foreignKeys = [ForeignKey(
        entity = OwnedDiscEntity::class, parentColumns = ["id"], childColumns = ["ownedDiscId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("ownedDiscId")]
)
data class OwnedDiscPhotoEntity(
    @PrimaryKey val id: String,
    val ownedDiscId: String,
    val localPath: String,
    val type: String,
    val capturedAt: Long
)
```

- [ ] **Step 8: Write `LostDiscEventEntity.kt`**

```kotlin
package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lost_disc_events",
    foreignKeys = [ForeignKey(
        entity = OwnedDiscEntity::class, parentColumns = ["id"], childColumns = ["ownedDiscId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("ownedDiscId", "foundAt")]
)
data class LostDiscEventEntity(
    @PrimaryKey val id: String,
    val ownedDiscId: String,
    val lostAt: Long,
    val lat: Double?,
    val lng: Double?,
    val courseName: String?,
    val holeNumber: Int?,
    val notes: String?,
    val foundAt: Long?
)
```

- [ ] **Step 9: Write `WishlistItemEntity.kt`**

```kotlin
package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wishlist_items",
    foreignKeys = [ForeignKey(
        entity = DiscEntity::class, parentColumns = ["id"], childColumns = ["discId"]
    )],
    indices = [Index("discId")]
)
data class WishlistItemEntity(
    @PrimaryKey val id: String,
    val discId: String,
    val addedAt: Long,
    val targetWeight: Int?,
    val targetPlastic: String?,
    val notes: String?
)
```

- [ ] **Step 10: Write `DiscDbMetaEntity.kt`**

```kotlin
package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disc_db_meta")
data class DiscDbMetaEntity(
    @PrimaryKey val id: Int = 1,
    val lastSyncedAt: Long,
    val etag: String?,
    val discCount: Int,
    val schemaVersion: Int
)
```

- [ ] **Step 11: Write `IdSubmissionQueueEntity.kt`**

```kotlin
package com.inknironapps.bagger.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "id_submission_queue")
data class IdSubmissionQueueEntity(
    @PrimaryKey val id: String,
    val photoPath: String,
    val confirmedDiscId: String,
    val ocrTokens: List<String>,
    val capturedAt: Long
)
```

- [ ] **Step 12: Write DAOs**

```kotlin
// app/src/main/java/com/inknironapps/bagger/data/db/dao/DiscDao.kt
package com.inknironapps.bagger.data.db.dao

import androidx.room.*
import com.inknironapps.bagger.data.db.entity.DiscEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscDao {
    @Upsert suspend fun upsertAll(discs: List<DiscEntity>)
    @Query("SELECT * FROM discs WHERE id = :id") suspend fun getById(id: String): DiscEntity?
    @Query("SELECT * FROM discs ORDER BY brand, mold") fun observeAll(): Flow<List<DiscEntity>>
    @Query("SELECT COUNT(*) FROM discs") suspend fun count(): Int
    @Query("SELECT * FROM discs WHERE LOWER(brand) LIKE '%' || LOWER(:q) || '%' OR LOWER(mold) LIKE '%' || LOWER(:q) || '%'")
    fun search(q: String): Flow<List<DiscEntity>>
}
```

```kotlin
// app/src/main/java/com/inknironapps/bagger/data/db/dao/OwnedDiscDao.kt
package com.inknironapps.bagger.data.db.dao

import androidx.room.*
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedDiscDao {
    @Upsert suspend fun upsert(disc: OwnedDiscEntity)
    @Delete suspend fun delete(disc: OwnedDiscEntity)
    @Query("SELECT * FROM owned_discs ORDER BY updatedAt DESC") suspend fun getAll(): List<OwnedDiscEntity>
    @Query("SELECT * FROM owned_discs ORDER BY updatedAt DESC") fun observeAll(): Flow<List<OwnedDiscEntity>>
    @Query("SELECT * FROM owned_discs WHERE id = :id") fun observeById(id: String): Flow<OwnedDiscEntity?>
    @Query("SELECT * FROM owned_discs WHERE state = :state") fun observeByState(state: String): Flow<List<OwnedDiscEntity>>
    @Query("SELECT * FROM owned_discs WHERE bagId = :bagId") fun observeByBag(bagId: String): Flow<List<OwnedDiscEntity>>
}
```

```kotlin
// app/src/main/java/com/inknironapps/bagger/data/db/dao/BagDao.kt
package com.inknironapps.bagger.data.db.dao

import androidx.room.*
import com.inknironapps.bagger.data.db.entity.BagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BagDao {
    @Upsert suspend fun upsert(bag: BagEntity)
    @Delete suspend fun delete(bag: BagEntity)
    @Query("SELECT * FROM bags ORDER BY sortOrder, name") fun observeAll(): Flow<List<BagEntity>>
    @Query("SELECT * FROM bags WHERE id = :id") suspend fun getById(id: String): BagEntity?
}
```

```kotlin
// app/src/main/java/com/inknironapps/bagger/data/db/dao/LostDiscEventDao.kt
package com.inknironapps.bagger.data.db.dao

import androidx.room.*
import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LostDiscEventDao {
    @Upsert suspend fun upsert(event: LostDiscEventEntity)
    @Query("SELECT * FROM lost_disc_events WHERE foundAt IS NULL ORDER BY lostAt DESC")
    fun observeUnfound(): Flow<List<LostDiscEventEntity>>
    @Query("SELECT * FROM lost_disc_events WHERE ownedDiscId = :id ORDER BY lostAt DESC")
    fun observeForDisc(id: String): Flow<List<LostDiscEventEntity>>
}
```

```kotlin
// app/src/main/java/com/inknironapps/bagger/data/db/dao/WishlistDao.kt
package com.inknironapps.bagger.data.db.dao

import androidx.room.*
import com.inknironapps.bagger.data.db.entity.WishlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Upsert suspend fun upsert(item: WishlistItemEntity)
    @Delete suspend fun delete(item: WishlistItemEntity)
    @Query("SELECT * FROM wishlist_items ORDER BY addedAt DESC") fun observeAll(): Flow<List<WishlistItemEntity>>
}
```

```kotlin
// app/src/main/java/com/inknironapps/bagger/data/db/dao/DiscDbMetaDao.kt
package com.inknironapps.bagger.data.db.dao

import androidx.room.*
import com.inknironapps.bagger.data.db.entity.DiscDbMetaEntity

@Dao
interface DiscDbMetaDao {
    @Upsert suspend fun upsert(meta: DiscDbMetaEntity)
    @Query("SELECT * FROM disc_db_meta WHERE id = 1") suspend fun get(): DiscDbMetaEntity?
}
```

- [ ] **Step 13: Write `BaggerDatabase.kt`**

```kotlin
package com.inknironapps.bagger.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inknironapps.bagger.data.db.dao.*
import com.inknironapps.bagger.data.db.entity.*

@Database(
    entities = [
        DiscEntity::class,
        OwnedDiscEntity::class,
        OwnedDiscPhotoEntity::class,
        BagEntity::class,
        LostDiscEventEntity::class,
        WishlistItemEntity::class,
        DiscDbMetaEntity::class,
        IdSubmissionQueueEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BaggerDatabase : RoomDatabase() {
    abstract fun discDao(): DiscDao
    abstract fun ownedDiscDao(): OwnedDiscDao
    abstract fun bagDao(): BagDao
    abstract fun lostDiscEventDao(): LostDiscEventDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun discDbMetaDao(): DiscDbMetaDao
}
```

- [ ] **Step 14: Configure Room schema export dir**

Add to `app/build.gradle.kts` inside `defaultConfig`:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
```

Add `app/schemas/` directory to source control (Room export-schema target).

- [ ] **Step 15: Run instrumented test on emulator/device, verify PASS**

Pre-req: emulator running OR device connected (`adb devices`).
Run: `./gradlew :app:connectedPlaystoreDebugAndroidTest --tests com.inknironapps.bagger.data.db.BaggerDatabaseTest`
Expected: 2 tests passing.

- [ ] **Step 16: Commit**

```bash
git add app/src/main/java/com/inknironapps/bagger/data/db/ app/src/androidTest/ app/schemas/ app/build.gradle.kts
git commit -m "feat: add Room DB with all phase-1 entities + DAOs

- 8 entities: Disc, OwnedDisc, OwnedDiscPhoto, Bag, LostDiscEvent,
  WishlistItem, DiscDbMeta, IdSubmissionQueue
- 6 DAOs covering CRUD + observation flows
- TypeConverter for List<String> custom tags
- Foreign keys + indices per spec section 4
- Schema exported to app/schemas/"
```

---

## Task 5: DataStore Preferences Wrapper

**Files:**
- Create: `app/src/main/java/com/inknironapps/bagger/data/prefs/BaggerPrefs.kt`
- Test: `app/src/test/java/com/inknironapps/bagger/data/prefs/BaggerPrefsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/inknironapps/bagger/data/prefs/BaggerPrefsTest.kt
package com.inknironapps.bagger.data.prefs

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class BaggerPrefsTest {
    @Test fun setAndReadThemeMode() = runTest {
        val prefs = BaggerPrefs(ApplicationProvider.getApplicationContext())
        prefs.setThemeMode("dark")
        prefs.themeMode.test {
            assertEquals("dark", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun defaultThemeIsSystem() = runTest {
        val prefs = BaggerPrefs(ApplicationProvider.getApplicationContext())
        prefs.themeMode.test {
            assertEquals("system", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test, verify FAIL**

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests com.inknironapps.bagger.data.prefs.BaggerPrefsTest`
Expected: FAIL — `BaggerPrefs` not defined.

- [ ] **Step 3: Write `BaggerPrefs.kt`**

```kotlin
package com.inknironapps.bagger.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "bagger_prefs")

@Singleton
class BaggerPrefs @Inject constructor(private val context: Context) {

    private object Keys {
        val THEME_MODE                  = stringPreferencesKey("theme_mode")             // "system"|"light"|"dark"
        val LAST_SEEN_CHANGELOG_VERSION = stringPreferencesKey("last_seen_changelog_version")
        val ID_TRAINING_CONSENT         = booleanPreferencesKey("id_training_consent")
        val LAST_DISC_DB_SYNC           = longPreferencesKey("last_disc_db_sync")
        val ONBOARDING_COMPLETE         = booleanPreferencesKey("onboarding_complete")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[Keys.THEME_MODE] = mode } }

    val lastSeenChangelogVersion: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_SEEN_CHANGELOG_VERSION] }
    suspend fun setLastSeenChangelogVersion(v: String) { context.dataStore.edit { it[Keys.LAST_SEEN_CHANGELOG_VERSION] = v } }

    val idTrainingConsent: Flow<Boolean> = context.dataStore.data.map { it[Keys.ID_TRAINING_CONSENT] ?: false }
    suspend fun setIdTrainingConsent(v: Boolean) { context.dataStore.edit { it[Keys.ID_TRAINING_CONSENT] = v } }

    val lastDiscDbSync: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_DISC_DB_SYNC] ?: 0L }
    suspend fun setLastDiscDbSync(t: Long) { context.dataStore.edit { it[Keys.LAST_DISC_DB_SYNC] = t } }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }
    suspend fun setOnboardingComplete(v: Boolean) { context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = v } }
}
```

- [ ] **Step 4: Run test, verify PASS**

Run: `./gradlew :app:testPlaystoreDebugUnitTest --tests com.inknironapps.bagger.data.prefs.BaggerPrefsTest`
Expected: 2 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/inknironapps/bagger/data/prefs/ app/src/test/java/com/inknironapps/bagger/data/prefs/
git commit -m "feat: add DataStore prefs wrapper for theme + onboarding + sync"
```

---

## Task 6: Hilt DI Modules

**Files:**
- Create: `app/src/main/java/com/inknironapps/bagger/di/DatabaseModule.kt`
- Create: `app/src/main/java/com/inknironapps/bagger/di/PrefsModule.kt`

- [ ] **Step 1: Write `DatabaseModule.kt`**

```kotlin
package com.inknironapps.bagger.di

import android.content.Context
import androidx.room.Room
import com.inknironapps.bagger.data.db.BaggerDatabase
import com.inknironapps.bagger.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BaggerDatabase =
        Room.databaseBuilder(context, BaggerDatabase::class.java, "bagger.db")
            .fallbackToDestructiveMigrationOnDowngrade(true)   // Plan 1 only; real migrations in later plans
            .build()

    @Provides fun provideDiscDao(db: BaggerDatabase): DiscDao = db.discDao()
    @Provides fun provideOwnedDiscDao(db: BaggerDatabase): OwnedDiscDao = db.ownedDiscDao()
    @Provides fun provideBagDao(db: BaggerDatabase): BagDao = db.bagDao()
    @Provides fun provideLostDiscEventDao(db: BaggerDatabase): LostDiscEventDao = db.lostDiscEventDao()
    @Provides fun provideWishlistDao(db: BaggerDatabase): WishlistDao = db.wishlistDao()
    @Provides fun provideDiscDbMetaDao(db: BaggerDatabase): DiscDbMetaDao = db.discDbMetaDao()
}
```

- [ ] **Step 2: Write `PrefsModule.kt`**

(BaggerPrefs is `@Singleton @Inject constructor` — Hilt provides automatically; this module reserved for future bindings.)

```kotlin
package com.inknironapps.bagger.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PrefsModule
```

- [ ] **Step 3: Build to confirm DI graph compiles**

Run: `./gradlew :app:assemblePlaystoreDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/inknironapps/bagger/di/
git commit -m "feat: add Hilt DI modules for Room + prefs"
```

---

## Task 7: Compose Navigation Skeleton + Bottom Nav

**Files:**
- Create: `app/src/main/java/com/inknironapps/bagger/ui/nav/Destinations.kt`
- Create: `app/src/main/java/com/inknironapps/bagger/ui/nav/BottomNav.kt`
- Create: `app/src/main/java/com/inknironapps/bagger/ui/nav/BaggerNavHost.kt`
- Create: 4 placeholder screens under `ui/screens/`

- [ ] **Step 1: Write `Destinations.kt`**

```kotlin
package com.inknironapps.bagger.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Shelf    : Destination("shelf",    "Shelf",    Icons.Filled.Inventory2)
    data object Bags     : Destination("bags",     "Bags",     Icons.Filled.Backpack)
    data object Discover : Destination("discover", "Discover", Icons.Filled.Search)
    data object More     : Destination("more",     "More",     Icons.Filled.MoreHoriz)
}

val BottomDestinations: List<Destination> = listOf(
    Destination.Shelf, Destination.Bags, Destination.Discover, Destination.More
)
```

- [ ] **Step 2: Write `BottomNav.kt`**

```kotlin
package com.inknironapps.bagger.ui.nav

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BaggerBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    NavigationBar {
        BottomDestinations.forEach { dest ->
            NavigationBarItem(
                selected = current == dest.route,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) }
            )
        }
    }
}
```

- [ ] **Step 3: Write 4 placeholder screens**

```kotlin
// app/src/main/java/com/inknironapps/bagger/ui/screens/ShelfScreen.kt
package com.inknironapps.bagger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable fun ShelfScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Shelf — Plan 3") }
}
```

(Repeat for `BagsScreen`, `DiscoverScreen`, `MoreScreen` with respective placeholder text.)

- [ ] **Step 4: Write `BaggerNavHost.kt`**

```kotlin
package com.inknironapps.bagger.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.inknironapps.bagger.ui.screens.*

@Composable
fun BaggerNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Destination.Shelf.route) {
        composable(Destination.Shelf.route)    { ShelfScreen() }
        composable(Destination.Bags.route)     { BagsScreen() }
        composable(Destination.Discover.route) { DiscoverScreen() }
        composable(Destination.More.route)     { MoreScreen() }
    }
}
```

- [ ] **Step 5: Write `MainActivity.kt`**

```kotlin
package com.inknironapps.bagger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.inknironapps.bagger.data.prefs.BaggerPrefs
import com.inknironapps.bagger.ui.nav.BaggerBottomBar
import com.inknironapps.bagger.ui.nav.BaggerNavHost
import com.inknironapps.bagger.ui.theme.BaggerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: BaggerPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "system")
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            BaggerTheme(darkTheme = isDark) {
                val nav = rememberNavController()
                Scaffold(bottomBar = { BaggerBottomBar(nav) }) { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                        BaggerNavHost(nav)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Build + install + manual smoke**

```bash
./gradlew :app:installPlaystoreDebug
adb shell am start -n com.inknironapps.bagger.debug/com.inknironapps.bagger.MainActivity
```

Expected: app launches w/ splash → bottom bar w/ 4 tabs visible. Tap each tab → respective placeholder text shows. No crashes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/inknironapps/bagger/MainActivity.kt app/src/main/java/com/inknironapps/bagger/ui/
git commit -m "feat: bottom-nav skeleton w/ 4 tabs + splash + theme switching"
```

---

## Task 8: Smoke UI Test for Navigation

**Files:**
- Test: `app/src/androidTest/java/com/inknironapps/bagger/MainActivityNavTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.inknironapps.bagger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MainActivityNavTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun shelfTabIsDefault() {
        compose.onNodeWithText("Shelf — Plan 3").assertIsDisplayed()
    }

    @Test fun tappingBagsShowsBagsScreen() {
        compose.onNodeWithText("Bags").performClick()
        compose.onNodeWithText("Bags — Plan 3").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Add Hilt test deps + custom test runner**

Add to `app/build.gradle.kts` dependencies:

```kotlin
androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
kspAndroidTest("com.google.dagger:hilt-android-compiler:2.52")
```

Update `defaultConfig` testInstrumentationRunner:

```kotlin
testInstrumentationRunner = "com.inknironapps.bagger.HiltTestRunner"
```

Add custom runner:

```kotlin
// app/src/androidTest/java/com/inknironapps/bagger/HiltTestRunner.kt
package com.inknironapps.bagger

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, ctx: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
}
```

- [ ] **Step 3: Run test, verify PASS**

Run: `./gradlew :app:connectedPlaystoreDebugAndroidTest --tests com.inknironapps.bagger.MainActivityNavTest`
Expected: 2 tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/
git commit -m "test: add nav smoke test + Hilt instrumented test runner"
```

---

## Task 9: Bundled Disc DB Fixture (10 discs for dev)

**Files:**
- Create: `app/src/main/assets/discs-baseline.json`
- Create: `app/src/main/java/com/inknironapps/bagger/data/db/seed/BaselineDiscLoader.kt`
- Test: `app/src/androidTest/java/com/inknironapps/bagger/data/db/seed/BaselineDiscLoaderTest.kt`

- [ ] **Step 1: Write `assets/discs-baseline.json`**

```json
[
  {"id":"innova-aviar","brand":"Innova","mold":"Aviar","speed":2,"glide":3,"turn":0,"fade":1,"discType":"Putter","stability":"stable","pdgaApproved":true,"yearReleased":1985,"primaryStampUrl":null,"aliases":[]},
  {"id":"innova-destroyer","brand":"Innova","mold":"Destroyer","speed":12,"glide":5,"turn":-1,"fade":3,"discType":"Driver","stability":"overstable","pdgaApproved":true,"yearReleased":2008,"primaryStampUrl":null,"aliases":["Star Destroyer"]},
  {"id":"discraft-buzzz","brand":"Discraft","mold":"Buzzz","speed":5,"glide":4,"turn":-1,"fade":1,"discType":"Mid","stability":"stable","pdgaApproved":true,"yearReleased":2003,"primaryStampUrl":null,"aliases":[]},
  {"id":"discraft-zone","brand":"Discraft","mold":"Zone","speed":4,"glide":3,"turn":0,"fade":3,"discType":"Approach","stability":"overstable","pdgaApproved":true,"yearReleased":2014,"primaryStampUrl":null,"aliases":[]},
  {"id":"mvp-tesla","brand":"MVP","mold":"Tesla","speed":9,"glide":5,"turn":-1,"fade":2,"discType":"Fairway","stability":"stable","pdgaApproved":true,"yearReleased":2014,"primaryStampUrl":null,"aliases":[]},
  {"id":"dynamic-judge","brand":"Dynamic Discs","mold":"Judge","speed":2,"glide":4,"turn":0,"fade":1,"discType":"Putter","stability":"stable","pdgaApproved":true,"yearReleased":2013,"primaryStampUrl":null,"aliases":[]},
  {"id":"latitude64-pure","brand":"Latitude 64","mold":"Pure","speed":3,"glide":3,"turn":-1,"fade":1,"discType":"Putter","stability":"understable","pdgaApproved":true,"yearReleased":2013,"primaryStampUrl":null,"aliases":[]},
  {"id":"discmania-fd","brand":"Discmania","mold":"FD","speed":7,"glide":6,"turn":-1,"fade":1,"discType":"Fairway","stability":"stable","pdgaApproved":true,"yearReleased":2009,"primaryStampUrl":null,"aliases":["Jackal"]},
  {"id":"prodigy-h3","brand":"Prodigy","mold":"H3 V2","speed":7,"glide":5,"turn":0,"fade":2,"discType":"Fairway","stability":"overstable","pdgaApproved":true,"yearReleased":2017,"primaryStampUrl":null,"aliases":[]},
  {"id":"westside-harp","brand":"Westside","mold":"Harp","speed":4,"glide":3,"turn":0,"fade":3,"discType":"Approach","stability":"overstable","pdgaApproved":true,"yearReleased":2014,"primaryStampUrl":null,"aliases":[]}
]
```

- [ ] **Step 2: Write the failing test**

```kotlin
// app/src/androidTest/java/com/inknironapps/bagger/data/db/seed/BaselineDiscLoaderTest.kt
package com.inknironapps.bagger.data.db.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inknironapps.bagger.data.db.BaggerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class BaselineDiscLoaderTest {
    private lateinit var db: BaggerDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BaggerDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    @Test fun loadsBaselineDiscs() = runBlocking {
        val loader = BaselineDiscLoader(ApplicationProvider.getApplicationContext(), db.discDao())
        loader.loadIfEmpty()
        assertEquals(10, db.discDao().count())
    }
}
```

- [ ] **Step 3: Run, verify FAIL**

Run: `./gradlew :app:connectedPlaystoreDebugAndroidTest --tests com.inknironapps.bagger.data.db.seed.BaselineDiscLoaderTest`
Expected: compile error (loader not defined).

- [ ] **Step 4: Write `BaselineDiscLoader.kt`**

```kotlin
package com.inknironapps.bagger.data.db.seed

import android.content.Context
import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.data.db.entity.DiscEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaselineDiscLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val discDao: DiscDao
) {
    suspend fun loadIfEmpty() {
        if (discDao.count() > 0) return
        val raw = context.assets.open("discs-baseline.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        val discs = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DiscEntity(
                id = o.getString("id"),
                brand = o.getString("brand"),
                mold = o.getString("mold"),
                speed = o.getDouble("speed").toFloat(),
                glide = o.getDouble("glide").toFloat(),
                turn = o.getDouble("turn").toFloat(),
                fade = o.getDouble("fade").toFloat(),
                discType = o.getString("discType"),
                stability = o.getString("stability"),
                pdgaApproved = o.getBoolean("pdgaApproved"),
                yearReleased = if (o.isNull("yearReleased")) null else o.getInt("yearReleased"),
                primaryStampUrl = if (o.isNull("primaryStampUrl")) null else o.getString("primaryStampUrl")
            )
        }
        discDao.upsertAll(discs)
    }
}
```

- [ ] **Step 5: Run test, verify PASS**

Run: `./gradlew :app:connectedPlaystoreDebugAndroidTest --tests com.inknironapps.bagger.data.db.seed.BaselineDiscLoaderTest`
Expected: 1 test passing.

- [ ] **Step 6: Wire loader into app launch**

Add to `BaggerApp.kt`:

```kotlin
package com.inknironapps.bagger

import android.app.Application
import com.inknironapps.bagger.data.db.seed.BaselineDiscLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BaggerApp : Application() {

    @Inject lateinit var baselineDiscLoader: BaselineDiscLoader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch { baselineDiscLoader.loadIfEmpty() }
    }
}
```

- [ ] **Step 7: Verify build + manual launch loads discs**

```bash
./gradlew :app:installPlaystoreDebug
adb shell am start -n com.inknironapps.bagger.debug/com.inknironapps.bagger.MainActivity
adb shell run-as com.inknironapps.bagger.debug sqlite3 databases/bagger.db "SELECT COUNT(*) FROM discs;"
```

Expected: query returns `10`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/assets/ app/src/main/java/com/inknironapps/bagger/data/db/seed/ app/src/main/java/com/inknironapps/bagger/BaggerApp.kt app/src/androidTest/java/com/inknironapps/bagger/data/db/seed/
git commit -m "feat: bundle 10-disc baseline fixture + loader on first launch"
```

---

## Task 10: Project Docs — CHANGELOG (LICENSE + README pre-done)

**Files:**
- Already committed: `LICENSE` (GPLv3) — commit `dbcea65`
- Already committed: `README.md` — commit `dbcea65`
- Create: `CHANGELOG.md` (public, Keep a Changelog format)

- [x] **Step 1: LICENSE present** (`curl -sL https://www.gnu.org/licenses/gpl-3.0.txt -o LICENSE` already executed)

- [x] **Step 2: README present** (front-loaded ahead of subagent execution)

If you are a subagent: skip steps 1–2, both files exist. Run `head -2 LICENSE` to confirm GPLv3, run `ls README.md` to confirm presence. Proceed to step 3.

> **Original step-2 README content (kept for reference, do not re-write):**

```markdown
# Bagger

Open-source disc-golf disc cataloging app for Android. Snap a photo of a disc and Bagger identifies it from on-device OCR, stores it on your shelf with flight numbers, and helps you organize multiple bags. Track lost discs on a map, maintain a wishlist, view stats, and export your inventory.

**Status:** alpha. Active development. Phase 1 (solo / local-only) underway.

## Features (Phase 1)

- Photo-based disc identification using on-device ML Kit OCR — no cloud, no accounts.
- Catalog of public disc database (sourced from PDGA approved list and manufacturer flight numbers).
- Multiple bags per user; move discs between bags freely.
- Full disc lifecycle states: shelf, in bag, lost, found, sold, traded, retired, gifted.
- Lost disc map with optional GPS pins and course/hole notes.
- Wishlist with one-tap conversion to owned disc when purchased.
- Inventory stats and CSV export.
- Local JSON backup and restore — no account required.
- Material 3 design with light, dark, and system theme support.

## Disc Database

Disc data lives in [`data/discs.json`](data/discs.json) within this repository. Community contributions welcome — open a pull request adding new discs or correcting flight numbers. CI validates the JSON schema automatically.

## Building from Source

Requirements:

- Android Studio Ladybug or newer
- JDK 21
- Android SDK 35

```bash
git clone https://github.com/LightWraith8268/bagger.git
cd bagger
./gradlew :app:installPlaystoreDebug
```

## License

GPLv3 — see [LICENSE](LICENSE). Forks must remain open source under the same terms.

## Contributing

See `CONTRIBUTING.md` (coming soon). Issues and PRs welcome.

## Brand

Bagger is published by Ink & Iron Apps.
```

- [ ] **Step 3: Write `CHANGELOG.md`** (public, Keep a Changelog format)

```markdown
# Changelog

All notable changes to Bagger are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial project scaffolding: Kotlin/Compose application with Hilt dependency injection and Room database.
- Material 3 theme using the Ink & Iron Apps palette and typography system.
- Bottom navigation with placeholder Shelf, Bags, Discover, and More tabs.
- Bundled fixture of ten common discs to support development.
```

- [ ] **Step 4: Verify file presence**

Run: `ls -la LICENSE README.md CHANGELOG.md`
Expected: all three files present, LICENSE > 30 KB.

- [ ] **Step 5: Commit**

```bash
git add LICENSE README.md CHANGELOG.md
git commit -m "docs: add GPLv3 license + README + initial changelog"
```

---

## Task 11: GitHub Actions — auto-merge.yml

**Files:**
- Create: `.github/workflows/auto-merge.yml`

- [ ] **Step 1: Write `auto-merge.yml`**

```yaml
name: Auto-merge claude/dev → main

on:
  push:
    branches: [claude/dev]
    paths-ignore:
      - 'data/**'
      - 'docs/**'
      - '*.md'
      - '.github/workflows/data-validate.yml'

permissions:
  contents: write
  pull-requests: write

jobs:
  auto-merge:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }

      - name: Create or update PR
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          set -euo pipefail
          existing=$(gh pr list --base main --head claude/dev --state open --json number -q '.[0].number' || echo "")
          if [ -z "$existing" ]; then
            gh pr create --base main --head claude/dev \
              --title "Auto-merge: claude/dev → main" \
              --body "Automated PR. CI must pass. See commit history for change details."
          else
            echo "PR #$existing already open."
          fi

      - name: Wait for required checks (none yet — Plan 1 has no CI tests)
        run: echo "Skipping check wait — release.yml runs after merge"

      - name: Merge with admin override
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          pr=$(gh pr list --base main --head claude/dev --state open --json number -q '.[0].number')
          gh pr merge "$pr" --admin --squash --delete-branch=false

      - name: Recreate claude/dev from main
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git fetch origin main
          git checkout -B claude/dev origin/main
          git push origin claude/dev --force-with-lease
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/auto-merge.yml
git commit -m "ci: add auto-merge workflow for claude/dev → main"
```

---

## Task 12: GitHub Actions — release.yml

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Write `release.yml`** (no signing/Play deploy yet — that's the Release plan)

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

      - name: Run unit tests
        run: ./gradlew :app:testPlaystoreDebugUnitTest

      - name: Lint
        run: ./gradlew :app:lintPlaystoreDebug

      - name: Build debug APK
        run: ./gradlew :app:assemblePlaystoreDebug

      - name: Upload debug APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: bagger-playstore-debug-${{ github.sha }}
          path: app/build/outputs/apk/playstore/debug/*.apk
          retention-days: 14
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: add release build workflow (debug only — signing follows in Release plan)"
```

---

## Task 13: GitHub Actions — data-validate.yml (stub, real validation Plan 2)

**Files:**
- Create: `.github/workflows/data-validate.yml`
- Create: `data/.gitkeep`

- [ ] **Step 1: Write `data/.gitkeep`** (placeholder so the directory exists)

```bash
touch data/.gitkeep
```

- [ ] **Step 2: Write `data-validate.yml`**

```yaml
name: Validate Disc Database

on:
  push:
    paths: ['data/**']
  pull_request:
    paths: ['data/**']

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: "3.13" }

      - name: Placeholder validation (real validate.py lands in Plan 2)
        run: |
          echo "Disc database validator stub. Plan 2 will add scripts/validate.py."
          if [ -f data/discs.json ]; then
            python -c "import json,sys; json.load(open('data/discs.json')); print('discs.json parses as JSON')"
          else
            echo "No discs.json yet — skipping."
          fi
```

- [ ] **Step 3: Commit**

```bash
git add data/.gitkeep .github/workflows/data-validate.yml
git commit -m "ci: add data-validate workflow stub for future disc DB"
```

---

## Task 14: Push, Verify CI Passes, Confirm Foundation

- [ ] **Step 1: Push `claude/dev`**

```bash
git push origin claude/dev
```

- [ ] **Step 2: Watch auto-merge workflow run**

```bash
gh run list --workflow=auto-merge.yml --limit 1
gh run watch
```

Expected: workflow completes successfully; PR opened, merged, branch recreated.

- [ ] **Step 3: Watch release workflow run on main**

```bash
gh run list --workflow=release.yml --limit 1
gh run watch
```

Expected: tests pass, lint passes, debug APK artifact uploaded.

- [ ] **Step 4: Manual emulator verification**

```bash
"$ANDROID_HOME"/emulator/emulator -list-avds
# pick AVD then:
"$ANDROID_HOME"/emulator/emulator -avd <AVD_NAME> &
adb wait-for-device
./gradlew :app:installPlaystoreDebug
adb shell am start -n com.inknironapps.bagger.debug/com.inknironapps.bagger.MainActivity
```

Verify:
- App launches with splash screen
- Bottom nav shows 4 tabs (Shelf, Bags, Discover, More)
- Tapping each tab shows its placeholder text
- DB seeded: `adb shell run-as com.inknironapps.bagger.debug sqlite3 databases/bagger.db "SELECT COUNT(*) FROM discs;"` returns `10`
- Theme respects system dark/light setting
- No crashes in logcat: `adb logcat -d | grep -E "AndroidRuntime|FATAL"`

- [ ] **Step 5: Capture verification screenshots**

```bash
adb exec-out screencap -p > docs/screenshots/plan1-shelf.png
# tap each tab manually, screencap each
```

- [ ] **Step 6: Update CHANGELOG with verified items**

Move "Initial project scaffolding..." entries into a new section:

```markdown
## [0.1.0] - 2026-04-30

### Added

- Initial project scaffolding: Kotlin/Compose application with Hilt dependency injection and Room database.
- Material 3 theme using the Ink & Iron Apps palette and typography system.
- Bottom navigation with placeholder Shelf, Bags, Discover, and More tabs.
- Bundled fixture of ten common discs to support development.
```

- [ ] **Step 7: Final commit**

```bash
git add CHANGELOG.md docs/screenshots/
git commit -m "docs: tag 0.1.0 in changelog + add Plan 1 verification screenshots"
git push origin claude/dev
```

---

## Verification Checklist (Plan 1 Done When All True)

- [ ] `./gradlew :app:assemblePlaystoreDebug` succeeds locally
- [ ] `./gradlew :app:testPlaystoreDebugUnitTest` reports all unit tests passing
- [ ] `./gradlew :app:connectedPlaystoreDebugAndroidTest` reports all instrumented tests passing on emulator
- [ ] App installs on Android 12+ device/emulator and launches without crash
- [ ] Bottom nav shows 4 tabs and switches between them
- [ ] Room DB seeded with 10 fixture discs on first launch
- [ ] DataStore persists theme preference across restarts
- [ ] CI: `auto-merge.yml` succeeds end-to-end (PR open, merge, branch reset)
- [ ] CI: `release.yml` succeeds on main, uploads debug APK artifact
- [ ] CI: `data-validate.yml` stub runs without error
- [ ] LICENSE, README, CHANGELOG present and committed
- [ ] Spec coverage: every entity from spec section 4 defined; nav skeleton matches spec section 6 tabs

---

## Out of Scope for This Plan (Tracked for Future)

- **Plan 2:** Real disc DB scraping (PDGA + per-manufacturer), `discs.json` generation, sync worker, schema validation script
- **Plan 3:** OwnedDisc + Bag CRUD UI, Shelf list w/ filters, Discover catalog, manual disc add
- **Plan 4:** CameraX + ML Kit photo-ID pipeline, JaroWinkler matcher, confirm/pick screens
- **Plan 5:** Lifecycle features — lost disc tracking, Maps Compose view, wishlist, comparison
- **Plan 6:** Stats screen, backup/restore, CSV export, in-app update card, what's new dialog, settings polish
- **Plan 7:** Release pipeline — keystore, signing, AAB upload to Play Store, beta track, store listing

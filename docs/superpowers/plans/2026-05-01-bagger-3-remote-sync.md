# Bagger Plan 3 — App Remote Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use `- [ ]` syntax.

**Goal:** App fetches `data/discs.json` from `raw.githubusercontent.com/LightWraith8268/bagger/main/data/discs.json` via WorkManager-scheduled background sync. ETag-aware (304 short-circuit). Schema version check. Failure-tolerant (offline = use cached/baseline). Updates `DiscDbMeta` row each sync. Wires into `BaggerApp.onCreate` + 7-day periodic.

**Architecture:** OkHttp + Retrofit for HTTP. WorkManager (Hilt-integrated) for scheduling. Worker writes to Room via injected DAOs. `BaselineDiscLoader` (from Plan 1) stays — runs first; sync runs after w/ network. Sync-failure = silent fallback (baseline already loaded).

**Tech Stack:** OkHttp 4.12, Retrofit 2.11, kotlinx-serialization-json 1.7.3 (replace `org.json` for type-safe parsing), WorkManager 2.10, hilt-work 1.2, MockWebServer for tests.

---

## File Structure

```
app/src/main/java/com/inknironapps/bagger/
├── data/
│   ├── remote/
│   │   ├── DiscDbApi.kt              # Retrofit interface
│   │   └── DiscDto.kt                # JSON-serializable
│   └── sync/
│       ├── DiscDbSyncWorker.kt       # WorkManager Worker
│       └── DiscDbSyncScheduler.kt    # schedule periodic + one-shot
├── di/
│   ├── NetworkModule.kt              # OkHttp + Retrofit + Json
│   └── WorkerModule.kt               # HiltWorkerFactory binding
└── BaggerApp.kt                      # @HiltAndroidApp + Configuration.Provider

app/src/test/java/.../data/sync/
└── DiscDbSyncWorkerTest.kt           # MockWebServer-based
```

---

## Task 1: Deps + Hilt-WorkManager Integration

- [ ] **Step 1: Add to `gradle/libs.versions.toml` `[versions]`**

```toml
okhttp = "4.12.0"
retrofit = "2.11.0"
retrofit-kotlinx-serialization = "1.0.0"
kotlinx-serialization-json = "1.7.3"
workmanager = "2.10.0"
hilt-work = "1.2.0"
mockwebserver = "4.12.0"
```

- [ ] **Step 2: Add to `[libraries]`**

```toml
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version.ref = "retrofit-kotlinx-serialization" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization-json" }
workmanager = { module = "androidx.work:work-runtime-ktx", version.ref = "workmanager" }
hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "hilt-work" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hilt-work" }
mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "mockwebserver" }
```

- [ ] **Step 3: Add to `[plugins]`**

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 4: Add `kotlin-serialization` plugin to `app/build.gradle.kts` plugins block**

```kotlin
alias(libs.plugins.kotlin.serialization)
```

- [ ] **Step 5: Add deps in `app/build.gradle.kts` dependencies**

```kotlin
implementation(libs.okhttp)
implementation(libs.okhttp.logging)
implementation(libs.retrofit)
implementation(libs.retrofit.kotlinx.serialization)
implementation(libs.kotlinx.serialization.json)
implementation(libs.workmanager)
implementation(libs.hilt.work)
ksp(libs.hilt.work.compiler)

testImplementation(libs.mockwebserver)
```

- [ ] **Step 6: Build + verify**

```bash
./gradlew :app:assemblePlaystoreDebug
```

- [ ] **Step 7: Commit**

```
chore: add OkHttp + Retrofit + WorkManager + serialization deps
```

---

## Task 2: NetworkModule + DiscDbApi

**Files:**
- `app/src/main/java/com/inknironapps/bagger/data/remote/DiscDto.kt`
- `app/src/main/java/com/inknironapps/bagger/data/remote/DiscDbApi.kt`
- `app/src/main/java/com/inknironapps/bagger/di/NetworkModule.kt`

- [ ] **Step 1: Write `DiscDto.kt`**

```kotlin
package com.inknironapps.bagger.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscDto(
    val id: String,
    val brand: String,
    val mold: String,
    val speed: Float,
    val glide: Float,
    val turn: Float,
    val fade: Float,
    val discType: String,
    val stability: String,
    val pdgaApproved: Boolean,
    val yearReleased: Int? = null,
    val primaryStampUrl: String? = null,
    val aliases: List<String> = emptyList(),
    val schemaVersion: Int = 1
)
```

- [ ] **Step 2: Write `DiscDbApi.kt`**

```kotlin
package com.inknironapps.bagger.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface DiscDbApi {
    @GET
    suspend fun fetchDiscs(
        @Url url: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<List<DiscDto>>
}
```

- [ ] **Step 3: Write `NetworkModule.kt`**

```kotlin
package com.inknironapps.bagger.di

import com.inknironapps.bagger.data.remote.DiscDbApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun provideDiscDbApi(retrofit: Retrofit): DiscDbApi = retrofit.create(DiscDbApi::class.java)
}
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:assemblePlaystoreDebug
```

- [ ] **Step 5: Commit**

```
feat: add DiscDbApi (Retrofit) + NetworkModule
```

---

## Task 3: DiscDbSyncWorker

**Files:**
- `app/src/main/java/com/inknironapps/bagger/data/sync/DiscDbSyncWorker.kt`
- `app/src/main/java/com/inknironapps/bagger/data/sync/DiscDbSyncScheduler.kt`
- `app/src/main/java/com/inknironapps/bagger/di/WorkerModule.kt`
- Test: `app/src/test/java/com/inknironapps/bagger/data/sync/DiscDbSyncWorkerTest.kt`

- [ ] **Step 1: Write `DiscDbSyncWorker.kt`**

```kotlin
package com.inknironapps.bagger.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.data.db.dao.DiscDbMetaDao
import com.inknironapps.bagger.data.db.entity.DiscDbMetaEntity
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.remote.DiscDbApi
import com.inknironapps.bagger.data.remote.DiscDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DiscDbSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: DiscDbApi,
    private val discDao: DiscDao,
    private val metaDao: DiscDbMetaDao
) : CoroutineWorker(context, params) {

    companion object {
        const val DISCS_URL =
            "https://raw.githubusercontent.com/LightWraith8268/bagger/main/data/discs.json"
        const val SUPPORTED_SCHEMA_VERSION = 1
    }

    override suspend fun doWork(): Result = runCatching {
        val meta = metaDao.get()
        val resp = api.fetchDiscs(DISCS_URL, meta?.etag)

        if (resp.code() == 304) {
            metaDao.upsert(
                (meta ?: emptyMeta()).copy(lastSyncedAt = System.currentTimeMillis())
            )
            return@runCatching Result.success()
        }
        if (!resp.isSuccessful) return@runCatching Result.retry()

        val body = resp.body() ?: return@runCatching Result.retry()

        val unsupported = body.firstOrNull()?.schemaVersion?.let { it > SUPPORTED_SCHEMA_VERSION } ?: false
        if (unsupported) {
            // Lock sync — schema newer than app. Don't crash, don't upsert.
            metaDao.upsert(
                (meta ?: emptyMeta()).copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    schemaVersion = body.first().schemaVersion
                )
            )
            return@runCatching Result.failure()
        }

        val entities = body.map { it.toEntity() }
        discDao.upsertAll(entities)

        val newEtag = resp.headers()["ETag"]
        metaDao.upsert(
            DiscDbMetaEntity(
                id = 1,
                lastSyncedAt = System.currentTimeMillis(),
                etag = newEtag,
                discCount = entities.size,
                schemaVersion = SUPPORTED_SCHEMA_VERSION
            )
        )
        Result.success()
    }.getOrElse { Result.retry() }

    private fun emptyMeta() = DiscDbMetaEntity(
        id = 1,
        lastSyncedAt = 0,
        etag = null,
        discCount = 0,
        schemaVersion = SUPPORTED_SCHEMA_VERSION
    )

    private fun DiscDto.toEntity() = DiscEntity(
        id = id, brand = brand, mold = mold,
        speed = speed, glide = glide, turn = turn, fade = fade,
        discType = discType, stability = stability,
        pdgaApproved = pdgaApproved,
        yearReleased = yearReleased,
        primaryStampUrl = primaryStampUrl
    )
}
```

- [ ] **Step 2: Write `DiscDbSyncScheduler.kt`**

```kotlin
package com.inknironapps.bagger.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscDbSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ONE_TIME_NAME = "disc_db_sync_oneshot"
        const val PERIODIC_NAME = "disc_db_sync_periodic"
    }

    fun scheduleNow() {
        val req = OneTimeWorkRequestBuilder<DiscDbSyncWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, req)
    }

    fun schedulePeriodic() {
        val req = PeriodicWorkRequestBuilder<DiscDbSyncWorker>(7, TimeUnit.DAYS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
```

- [ ] **Step 3: Write `WorkerModule.kt`**

(Hilt-Work uses `@HiltWorker` + `@AssistedInject` — no explicit module needed for individual workers, but `BaggerApp` must implement `Configuration.Provider` and register `HiltWorkerFactory`.)

```kotlin
package com.inknironapps.bagger.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule
```

- [ ] **Step 4: Update `BaggerApp.kt`**

```kotlin
package com.inknironapps.bagger

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.inknironapps.bagger.data.db.seed.BaselineDiscLoader
import com.inknironapps.bagger.data.sync.DiscDbSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BaggerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var baselineDiscLoader: BaselineDiscLoader
    @Inject lateinit var syncScheduler: DiscDbSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            baselineDiscLoader.loadIfEmpty()
            syncScheduler.scheduleNow()
            syncScheduler.schedulePeriodic()
        }
    }
}
```

Remove the manifest meta-data for `WorkManagerInitializer` if Plan 1 added one (Plan 1 didn't — Configuration.Provider is the on-demand init pattern).

Add to `AndroidManifest.xml` `<application>` to disable default WorkManager initializer (since we provide config):

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

- [ ] **Step 5: Build**

- [ ] **Step 6: Commit**

```
feat: add DiscDbSyncWorker + scheduler with ETag-aware fetch
```

---

## Task 4: Sync Worker Test (MockWebServer)

**Files:**
- Test: `app/src/test/java/com/inknironapps/bagger/data/sync/DiscDbSyncWorkerTest.kt`

- [ ] **Step 1: Write test**

```kotlin
package com.inknironapps.bagger.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.data.db.dao.DiscDbMetaDao
import com.inknironapps.bagger.data.db.entity.DiscDbMetaEntity
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.remote.DiscDbApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DiscDbSyncWorkerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DiscDbApi

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DiscDbApi::class.java)
    }

    @After fun teardown() = server.shutdown()

    @Test fun successfulFetchUpsertsDiscsAndStoresEtag() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("ETag", "\"abc123\"")
                .setBody(
                    """[{"id":"innova-aviar","brand":"Innova","mold":"Aviar",
                       "speed":2,"glide":3,"turn":0,"fade":1,"discType":"Putter",
                       "stability":"stable","pdgaApproved":true,"schemaVersion":1}]"""
                )
        )

        val discDao = mockk<DiscDao>(relaxed = true)
        val metaDao = mockk<DiscDbMetaDao>(relaxed = true)
        coEvery { metaDao.get() } returns null
        val capture = slot<List<DiscEntity>>()
        coEvery { discDao.upsertAll(capture(capture)) } returns Unit

        val worker = TestListenableWorkerBuilder<DiscDbSyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(InjectingWorkerFactory(api, discDao, metaDao))
            .build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, capture.captured.size)
        assertEquals("innova-aviar", capture.captured[0].id)
        coVerify { metaDao.upsert(match { it.etag == "\"abc123\"" && it.discCount == 1 }) }
    }

    @Test fun notModified304ShortCircuits() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(304))

        val discDao = mockk<DiscDao>(relaxed = true)
        val metaDao = mockk<DiscDbMetaDao>(relaxed = true)
        coEvery { metaDao.get() } returns DiscDbMetaEntity(1, 0L, "\"abc\"", 5, 1)

        val worker = TestListenableWorkerBuilder<DiscDbSyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(InjectingWorkerFactory(api, discDao, metaDao))
            .build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { discDao.upsertAll(any()) }
    }

    @Test fun newerSchemaVersionMarksFailure() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":"x","brand":"X","mold":"Y","speed":1,"glide":1,"turn":0,
                   "fade":0,"discType":"Putter","stability":"stable","pdgaApproved":true,
                   "schemaVersion":99}]"""
            )
        )

        val discDao = mockk<DiscDao>(relaxed = true)
        val metaDao = mockk<DiscDbMetaDao>(relaxed = true)
        coEvery { metaDao.get() } returns null

        val worker = TestListenableWorkerBuilder<DiscDbSyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(InjectingWorkerFactory(api, discDao, metaDao))
            .build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { discDao.upsertAll(any()) }
    }
}

private class InjectingWorkerFactory(
    private val api: DiscDbApi,
    private val discDao: DiscDao,
    private val metaDao: DiscDbMetaDao
) : androidx.work.WorkerFactory() {
    override fun createWorker(
        appContext: android.content.Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): androidx.work.ListenableWorker = DiscDbSyncWorker(appContext, workerParameters, api, discDao, metaDao)
}
```

NOTE: requires `androidx.work:work-testing` test dep:

In `gradle/libs.versions.toml` `[libraries]`:
```toml
work-testing = { module = "androidx.work:work-testing", version.ref = "workmanager" }
```

In `app/build.gradle.kts`:
```kotlin
testImplementation(libs.work.testing)
```

- [ ] **Step 2: Run tests**

```bash
./gradlew :app:testPlaystoreDebugUnitTest --tests "com.inknironapps.bagger.data.sync.*"
```

Expected: 3 tests pass.

- [ ] **Step 3: Commit**

```
test: add DiscDbSyncWorker tests with MockWebServer
```

---

## Task 5: Push + Verify

- [ ] Push, watch auto-merge + release.yml.

- [ ] CHANGELOG entry:

```markdown
## [0.3.0] - 2026-05-XX

### Added

- App-side remote disc database sync. On launch, the app fetches the canonical `discs.json` from the public repository, applies it to the local Room cache, and stores the response ETag for cheap revalidation. Subsequent syncs run on a 7-day periodic WorkManager schedule with network constraints.
- Schema version handshake: if the remote catalog declares a newer schema than the installed app understands, sync stops gracefully without corrupting local data.
- Test suite for the sync worker using MockWebServer covering successful upsert, 304-not-modified short-circuit, and unsupported-schema failure paths.
```

- [ ] Commit + push.

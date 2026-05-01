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
        DiscDbSyncWorker.DISCS_URL = server.url("/discs.json").toString()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DiscDbApi::class.java)
    }

    @After fun teardown() {
        server.shutdown()
    }

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

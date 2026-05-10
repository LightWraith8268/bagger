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
        @Volatile
        var DISCS_URL: String =
            "https://raw.githubusercontent.com/LightWraith8268/bagger/main/data/discs.json"
        const val SUPPORTED_SCHEMA_VERSION = 2
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
        primaryStampUrl = primaryStampUrl,
        maxWeightG = maxWeightG,
        diameterCm = diameterCm,
        discClass = discClass
    )
}

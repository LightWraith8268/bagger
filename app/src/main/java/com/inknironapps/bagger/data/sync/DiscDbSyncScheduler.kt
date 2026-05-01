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

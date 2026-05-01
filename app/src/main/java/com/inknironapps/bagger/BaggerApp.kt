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

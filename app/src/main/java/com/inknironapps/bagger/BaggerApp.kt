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

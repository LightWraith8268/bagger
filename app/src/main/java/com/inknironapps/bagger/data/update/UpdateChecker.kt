package com.inknironapps.bagger.data.update

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class UpdateState {
    data object Idle : UpdateState()
    data object UpToDate : UpdateState()
    data class UpdateAvailable(val versionCode: Int) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

@Singleton
class UpdateChecker @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager by lazy { AppUpdateManagerFactory.create(context) }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    suspend fun check() {
        try {
            val info = await(manager.appUpdateInfo)
            _state.value = when (info.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> UpdateState.UpdateAvailable(info.availableVersionCode())
                else -> UpdateState.UpToDate
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "check failed")
        }
    }

    private suspend fun await(task: com.google.android.gms.tasks.Task<AppUpdateInfo>): AppUpdateInfo =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.cancel(it) }
        }
}

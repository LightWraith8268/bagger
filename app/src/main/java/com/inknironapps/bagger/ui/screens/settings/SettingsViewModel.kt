package com.inknironapps.bagger.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.BuildConfig
import com.inknironapps.bagger.data.backup.BackupExporter
import com.inknironapps.bagger.data.backup.BackupImporter
import com.inknironapps.bagger.data.backup.CsvExporter
import com.inknironapps.bagger.data.db.BaggerDatabase
import com.inknironapps.bagger.data.prefs.BaggerPrefs
import com.inknironapps.bagger.data.update.UpdateChecker
import com.inknironapps.bagger.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUi(
    val themeMode: String = "system",
    val versionName: String = BuildConfig.VERSION_NAME,
    val updateState: UpdateState = UpdateState.Idle
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: BaggerPrefs,
    private val backupExporter: BackupExporter,
    private val backupImporter: BackupImporter,
    private val csvExporter: CsvExporter,
    private val updateChecker: UpdateChecker,
    private val db: BaggerDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val ui: StateFlow<SettingsUi> =
        combine(prefs.themeMode, updateChecker.state) { theme, update ->
            SettingsUi(themeMode = theme, updateState = update)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUi())

    init {
        viewModelScope.launch { updateChecker.check() }
    }

    fun setTheme(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    suspend fun exportBackupJson(): String = backupExporter.export()

    suspend fun importBackupJson(text: String): String =
        when (val r = backupImporter.import(text)) {
            is BackupImporter.Result.Success -> "Restored ${r.counts.owned} discs, ${r.counts.bags} bags."
            is BackupImporter.Result.Failure -> r.message
        }

    suspend fun exportCsv(): String = csvExporter.export()

    fun deleteAllData() {
        viewModelScope.launch {
            db.clearAllTables()
        }
    }
}

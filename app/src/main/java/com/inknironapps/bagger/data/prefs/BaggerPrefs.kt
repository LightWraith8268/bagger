package com.inknironapps.bagger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "bagger_prefs")

internal fun dataStoreFor(context: Context): DataStore<Preferences> = context.dataStore

@Singleton
class BaggerPrefs @Inject constructor(private val context: Context) {

    private object Keys {
        val THEME_MODE                  = stringPreferencesKey("theme_mode")
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

package com.example.squadlink.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map



val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_DARK_THEME     = booleanPreferencesKey("dark_theme")
        val KEY_PLAYER_NAME    = stringPreferencesKey("player_name")
        val KEY_SHOW_GRID      = booleanPreferencesKey("show_grid")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_FIELD_ID       = stringPreferencesKey("selected_field_id")
        val KEY_ACTIVE_GAME    = stringPreferencesKey("active_game_code")
        val KEY_IS_GM          = booleanPreferencesKey("is_game_master")
        val KEY_ACTIVE_USER    = stringPreferencesKey("active_user_name")
    }

    val darkTheme: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_DARK_THEME] ?: false }

    val playerName: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_PLAYER_NAME] ?: "" }

    val showGrid: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_SHOW_GRID] ?: true }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_KEEP_SCREEN_ON] ?: true }

    val selectedFieldId: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_FIELD_ID] ?: "" }

    val activeGameCode: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_ACTIVE_GAME] ?: "" }

    val isGameMaster: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_IS_GM] ?: false }

    val activeUserName: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_ACTIVE_USER] ?: "" }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DARK_THEME] = enabled }
    }

    suspend fun setPlayerName(name: String) {
        context.dataStore.edit { it[KEY_PLAYER_NAME] = name }
    }

    suspend fun setShowGrid(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_GRID] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setSelectedFieldId(fieldId: String) {
        context.dataStore.edit { it[KEY_FIELD_ID] = fieldId }
    }

    suspend fun clearSelectedField() {
        context.dataStore.edit { it.remove(KEY_FIELD_ID) }
    }

    suspend fun setActiveGameCode(code: String) {
        context.dataStore.edit { it[KEY_ACTIVE_GAME] = code }
    }

    suspend fun clearActiveGameCode() {
        context.dataStore.edit { it.remove(KEY_ACTIVE_GAME) }
    }

    suspend fun setIsGameMaster(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_GM] = enabled }
    }

    suspend fun setActiveUserName(name: String) {
        context.dataStore.edit { it[KEY_ACTIVE_USER] = name }
    }

    suspend fun clearActiveUserName() {
        context.dataStore.edit { it.remove(KEY_ACTIVE_USER) }
    }
}

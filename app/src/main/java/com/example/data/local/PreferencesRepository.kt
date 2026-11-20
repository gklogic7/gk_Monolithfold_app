package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SortField
import com.example.model.ThemeMode
import com.example.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.monolithDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "monolith_preferences"
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val viewMode: ViewMode = ViewMode.LIST,
    val gridColumns: Int = 3,
    val sortField: SortField = SortField.NAME,
    val sortAscending: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val biometricEnabled: Boolean = true,
    val vaultAutoLockSeconds: Int = 60,
    val vaultPinHash: String? = null,
    val vaultPinSalt: String? = null
) {
    val hasVaultPin: Boolean get() = !vaultPinHash.isNullOrBlank() && !vaultPinSalt.isNullOrBlank()
}

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val VAULT_TIMEOUT_SECONDS = intPreferencesKey("vault_timeout_seconds")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val VAULT_PIN_SALT = stringPreferencesKey("vault_pin_salt")
    }

    val preferencesFlow: Flow<UserPreferences> = context.monolithDataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.SYSTEM,
            viewMode = prefs[Keys.VIEW_MODE]?.let { name ->
                ViewMode.entries.firstOrNull { it.name == name }
            } ?: ViewMode.LIST,
            gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(2, 6),
            sortField = prefs[Keys.SORT_FIELD]?.let { name ->
                SortField.entries.firstOrNull { it.name == name }
            } ?: SortField.NAME,
            sortAscending = prefs[Keys.SORT_ASCENDING] ?: true,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
            vaultAutoLockSeconds = prefs[Keys.VAULT_TIMEOUT_SECONDS] ?: 60,
            vaultPinHash = prefs[Keys.VAULT_PIN_HASH],
            vaultPinSalt = prefs[Keys.VAULT_PIN_SALT]
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.monolithDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.monolithDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setGridColumns(columns: Int) {
        context.monolithDataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(2, 6) }
    }

    suspend fun setSort(field: SortField, ascending: Boolean) {
        context.monolithDataStore.edit {
            it[Keys.SORT_FIELD] = field.name
            it[Keys.SORT_ASCENDING] = ascending
        }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.monolithDataStore.edit { it[Keys.SHOW_HIDDEN] = show }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.monolithDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setVaultAutoLockSeconds(seconds: Int) {
        context.monolithDataStore.edit { it[Keys.VAULT_TIMEOUT_SECONDS] = seconds.coerceAtLeast(0) }
    }

    suspend fun setVaultPinCredentials(hashBase64: String, saltBase64: String) {
        context.monolithDataStore.edit {
            it[Keys.VAULT_PIN_HASH] = hashBase64
            it[Keys.VAULT_PIN_SALT] = saltBase64
        }
    }
}

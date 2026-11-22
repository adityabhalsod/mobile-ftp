package com.mobileftp.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobileftp.domain.model.ServerConfig
import com.mobileftp.ui.theme.ThemePreference
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "mobile_ftp_settings")

/** Application-wide non-sensitive settings (DataStore). Sensitive creds live in SecurePreferences. */
class SettingsStore(
    private val context: Context,
    securePreferencesProvider: () -> SecurePreferences = { SecurePreferences(context) }
) {

    /**
     * Encrypted prefs are expensive to open (Keystore + Tink). Previously a new
     * instance was created on *every* DataStore emission, on the main thread,
     * which made the Server screen stutter and freeze after a few saves.
     * Open it once, lazily, and tolerate Keystore failures.
     */
    private val secure: SecurePreferences? by lazy {
        runCatching(securePreferencesProvider)
            .onFailure { Log.e(TAG, "Unable to open encrypted preferences", it) }
            .getOrNull()
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val SERVER_PORT = intPreferencesKey("server_port")
        val SERVER_ROOT_URI = stringPreferencesKey("server_root_uri")
        val SERVER_ROOT_PATH = stringPreferencesKey("server_root_path")
        val PASV_START = intPreferencesKey("pasv_start")
        val PASV_END = intPreferencesKey("pasv_end")
        val MAX_CONNECTIONS = intPreferencesKey("max_connections")
        val MAX_PER_IP = intPreferencesKey("max_per_ip")
        val ANON = booleanPreferencesKey("anonymous")
        val CHUNKS = intPreferencesKey("chunk_count")
        val FTPS = booleanPreferencesKey("ftps_enabled")
        val COMPRESSION_ENABLED = booleanPreferencesKey("compression_enabled")
        val ADAPTIVE_BUFFER = booleanPreferencesKey("adaptive_buffer")
        /**
         * Bumped on every save. Credentials live in encrypted prefs, outside
         * DataStore, so without this a save that only changed the username or
         * password produced no DataStore emission and the UI kept stale values.
         */
        val CONFIG_REVISION = longPreferencesKey("server_config_revision")
    }

    val themeFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        runCatching { ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.SYSTEM.name) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    suspend fun setTheme(value: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = value.name }
    }

    val serverConfigFlow: Flow<ServerConfig> = context.dataStore.data
        .map(::mapServerConfig)
        .distinctUntilChanged()
        // Decrypting credentials touches disk + Keystore: keep it off the main thread.
        .flowOn(Dispatchers.IO)

    private fun mapServerConfig(prefs: Preferences): ServerConfig {
        val defaults = ServerConfig()
        val sp = secure
        return ServerConfig(
            port = prefs[Keys.SERVER_PORT] ?: defaults.port,
            username = runCatching { sp?.getString(SecurePreferences.KEY_SERVER_USERNAME, defaults.username) }
                .getOrNull() ?: defaults.username,
            password = runCatching { sp?.getString(SecurePreferences.KEY_SERVER_PASSWORD, defaults.password) }
                .getOrNull() ?: defaults.password,
            rootDirectoryUri = prefs[Keys.SERVER_ROOT_URI].orEmpty(),
            rootDirectoryPath = prefs[Keys.SERVER_ROOT_PATH].orEmpty(),
            pasvPortStart = prefs[Keys.PASV_START] ?: 50000,
            pasvPortEnd = prefs[Keys.PASV_END] ?: 51000,
            maxConnections = prefs[Keys.MAX_CONNECTIONS] ?: 10,
            maxConnectionsPerIp = prefs[Keys.MAX_PER_IP] ?: 4,
            anonymousAccess = prefs[Keys.ANON] ?: false,
            chunkCount = prefs[Keys.CHUNKS] ?: 8,
            ftpsEnabled = prefs[Keys.FTPS] ?: false
        )
    }

    suspend fun saveServerConfig(config: ServerConfig) {
        withContext(Dispatchers.IO) {
            val sp = secure ?: error("Secure storage is unavailable on this device")
            // commit() (not apply()) so the values are on disk before DataStore emits.
            sp.putStringNow(SecurePreferences.KEY_SERVER_USERNAME, config.username)
            sp.putStringNow(SecurePreferences.KEY_SERVER_PASSWORD, config.password)
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.CONFIG_REVISION] = (prefs[Keys.CONFIG_REVISION] ?: 0L) + 1L
            prefs[Keys.SERVER_PORT] = config.port
            prefs[Keys.SERVER_ROOT_URI] = config.rootDirectoryUri
            prefs[Keys.SERVER_ROOT_PATH] = config.rootDirectoryPath
            prefs[Keys.PASV_START] = config.pasvPortStart
            prefs[Keys.PASV_END] = config.pasvPortEnd
            prefs[Keys.MAX_CONNECTIONS] = config.maxConnections
            prefs[Keys.MAX_PER_IP] = config.maxConnectionsPerIp
            prefs[Keys.ANON] = config.anonymousAccess
            prefs[Keys.CHUNKS] = config.chunkCount
            prefs[Keys.FTPS] = config.ftpsEnabled
        }
    }

    val compressionEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.COMPRESSION_ENABLED] ?: true }

    suspend fun setCompressionEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.COMPRESSION_ENABLED] = value }
    }

    val adaptiveBufferFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ADAPTIVE_BUFFER] ?: true }

    suspend fun setAdaptiveBuffer(value: Boolean) {
        context.dataStore.edit { it[Keys.ADAPTIVE_BUFFER] = value }
    }

    private companion object {
        const val TAG = "SettingsStore"
    }
}

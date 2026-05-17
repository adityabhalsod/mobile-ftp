package com.mobileftp.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobileftp.domain.model.ServerConfig
import com.mobileftp.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mobile_ftp_settings")

/** Application-wide non-sensitive settings (DataStore). Sensitive creds live in SecurePreferences. */
class SettingsStore(private val context: Context) {

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
    }

    val themeFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        runCatching { ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.SYSTEM.name) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    suspend fun setTheme(value: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = value.name }
    }

    val serverConfigFlow: Flow<ServerConfig> = context.dataStore.data.map(::mapServerConfig)

    private fun mapServerConfig(prefs: Preferences): ServerConfig {
        val secure = SecurePreferences(context)
        return ServerConfig(
            port = prefs[Keys.SERVER_PORT] ?: 2121,
            username = secure.getString(SecurePreferences.KEY_SERVER_USERNAME, "mobile"),
            password = secure.getString(SecurePreferences.KEY_SERVER_PASSWORD, "ftp"),
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
        val secure = SecurePreferences(context)
        secure.putString(SecurePreferences.KEY_SERVER_USERNAME, config.username)
        secure.putString(SecurePreferences.KEY_SERVER_PASSWORD, config.password)
        context.dataStore.edit { prefs ->
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
}

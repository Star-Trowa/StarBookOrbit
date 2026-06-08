package io.github.star_trowa.starbookorbit.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.star_trowa.starbookorbit.domain.model.ServerConfig
import io.github.star_trowa.starbookorbit.domain.model.ServerType
import io.github.star_trowa.starbookorbit.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private object Keys {
        const val PREFS_NAME = "secure_server_prefs"
        const val SERVER_URL = "server_url"
        const val SERVER_TYPE = "server_type"
    }

    // 1. Generate the encryption key
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // 2. Build the encrypted preferences file
    // TODO: Wrap this initialization in a coroutine block on Dispatchers.IO (to avoid sttuter)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        Keys.PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // 3. Keep a StateFlow so the UI can still observe changes reactively
    private val _serverConfig = MutableStateFlow<ServerConfig?>(
        sharedPreferences.getString(Keys.SERVER_URL, null)?.let { url ->
            val typeString = sharedPreferences.getString(Keys.SERVER_TYPE, ServerType.UNKNOWN.name)
            // Safely convert the string back to an Enum
            val type = runCatching { ServerType.valueOf(typeString!!) }.getOrDefault(ServerType.UNKNOWN)
            ServerConfig(url, type)
        }
    )

    override val serverConfig: Flow<ServerConfig?> = _serverConfig.asStateFlow()

    override suspend fun save(config: ServerConfig) {
        // Use the KTX extension block
        sharedPreferences.edit {
            putString(Keys.SERVER_URL, config.normalizedUrl)
            putString(Keys.SERVER_TYPE, config.type.name)
        }
        _serverConfig.value = config
    }

    override suspend fun clear() {
        // Use the KTX extension block
        sharedPreferences.edit {
            remove(Keys.SERVER_URL)
            remove(Keys.SERVER_TYPE)
        }
        _serverConfig.value = null
    }
}
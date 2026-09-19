package io.github.star_trowa.starbookorbit.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.star_trowa.starbookorbit.domain.model.ServerConfig
import io.github.star_trowa.starbookorbit.domain.model.ServerType
import io.github.star_trowa.starbookorbit.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private object Keys {
        const val PREFS_NAME = "secure_server_prefs"
        const val SERVER_URL = "server_url"
        const val SERVER_TYPE = "server_type"
    }

    private val appContext = context.applicationContext

    private val repositoryScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sharedPreferencesDeferred =
        CompletableDeferred<SharedPreferences>()

    private val _serverConfig = MutableStateFlow<ServerConfig?>(null)

    override val serverConfig: Flow<ServerConfig?> =
        flow {
            val preferences = sharedPreferencesDeferred.await()
            _serverConfig.value = readServerConfig(preferences)
            emitAll(_serverConfig)
        }

    init {
        repositoryScope.launch {
            runCatching {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    appContext,
                    Keys.PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.onSuccess { preferences ->
                _serverConfig.value = readServerConfig(preferences)

                sharedPreferencesDeferred.complete(preferences)
            }.onFailure { error ->
                sharedPreferencesDeferred.completeExceptionally(error)
            }
        }
    }

    private fun readServerConfig(
        preferences: SharedPreferences
    ): ServerConfig? {

        val url = preferences.getString(Keys.SERVER_URL, null)
            ?: return null

        val typeString =
            preferences.getString(
                Keys.SERVER_TYPE,
                ServerType.UNKNOWN.name
            )

        val type = runCatching {
            ServerType.valueOf(
                typeString ?: ServerType.UNKNOWN.name
            )
        }.getOrDefault(ServerType.UNKNOWN)

        return ServerConfig(url, type)
    }

    private suspend fun getPreferences(): SharedPreferences =
        sharedPreferencesDeferred.await()

    override suspend fun save(config: ServerConfig) {
        val preferences = getPreferences()

        withContext(Dispatchers.IO) {
            preferences.edit {
                putString(
                    Keys.SERVER_URL,
                    config.normalizedUrl
                )
                putString(
                    Keys.SERVER_TYPE,
                    config.type.name
                )
            }
        }

        _serverConfig.value = config
    }

    override suspend fun clear() {
        val preferences = getPreferences()

        withContext(Dispatchers.IO) {
            preferences.edit {
                remove(Keys.SERVER_URL)
                remove(Keys.SERVER_TYPE)
            }
        }
        _serverConfig.value = null
    }
}
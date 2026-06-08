package io.github.star_trowa.starbookorbit

import io.github.star_trowa.starbookorbit.domain.model.ServerConfig
import io.github.star_trowa.starbookorbit.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository : SettingsRepository {

    private val _serverConfig = MutableStateFlow<ServerConfig?>(null)
    override val serverConfig: Flow<ServerConfig?> = _serverConfig.asStateFlow()

    override suspend fun save(config: ServerConfig) {
        _serverConfig.value = config
    }

    override suspend fun clear() {
        _serverConfig.value = null
    }

    // Helper for tests to set up initial state before ViewModel init
    fun setInitialState(config: ServerConfig?) {
        _serverConfig.value = config
    }
}
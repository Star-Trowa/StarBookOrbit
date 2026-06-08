package io.github.star_trowa.starbookorbit.domain.repository

import io.github.star_trowa.starbookorbit.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val serverConfig: Flow<ServerConfig?>
    suspend fun save(config: ServerConfig)
    suspend fun clear()
}
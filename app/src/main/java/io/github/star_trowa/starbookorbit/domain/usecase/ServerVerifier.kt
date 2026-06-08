package io.github.star_trowa.starbookorbit.domain.usecase

import io.github.star_trowa.starbookorbit.domain.model.ServerType

interface ServerVerifier {
    val serverType: ServerType
    suspend fun verify(baseUrl: String): Boolean
}
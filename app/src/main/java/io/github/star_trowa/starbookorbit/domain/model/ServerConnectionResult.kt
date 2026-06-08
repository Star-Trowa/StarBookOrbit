package io.github.star_trowa.starbookorbit.domain.model

sealed interface ServerConnectionResult {
    data class Success(val type: ServerType) : ServerConnectionResult
    object UnsupportedServer : ServerConnectionResult // Reached the IP, but it's not BookOrbit
    object Unreachable : ServerConnectionResult // Timeout, No Internet, Connection Refused
}
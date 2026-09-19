package io.github.star_trowa.starbookorbit.domain.usecase

import io.github.star_trowa.starbookorbit.domain.model.ServerConnectionResult
import kotlinx.coroutines.CancellationException

class CheckServerStatusUseCase(
    private val verifiers: List<ServerVerifier> = listOf(BookOrbitVerifier())
) {
    suspend operator fun invoke(targetUrl: String): ServerConnectionResult {
        val cleanUrl = targetUrl.removeSuffix("/")

        for (verifier in verifiers) {
            try {
                if (verifier.verify(cleanUrl)) {
                    return ServerConnectionResult.Success(verifier.serverType)
                }
            } catch (_: java.io.IOException) {
                // If the IP is dead, it's dead for all verifiers. Bail immediately.
                return ServerConnectionResult.Unreachable
            }
        }
        // Reached the IP, but no verifier claimed it
        return ServerConnectionResult.UnsupportedServer
    }
}
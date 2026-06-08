package io.github.star_trowa.starbookorbit.domain.usecase

import io.github.star_trowa.starbookorbit.domain.model.ServerConfig

class ValidateUrlUseCase {

    operator fun invoke(input: String): Result {
        val trimmed = input.trim()
        return when {
            trimmed.isBlank() ->
                Result.Invalid("URL cannot be empty")

            !trimmed.startsWith("http://") && !trimmed.startsWith("https://") ->
                Result.Invalid("Must start with http:// or https://")

            // NEW: Ensure there is actually a host/IP after the scheme
            trimmed.substringAfter("://").isBlank() ->
                Result.Invalid("Please enter the server address after http://")

            else ->
                Result.Valid(ServerConfig(trimmed))
        }
    }

    sealed interface Result {
        data class Valid(val config: ServerConfig) : Result
        data class Invalid(val reason: String) : Result
    }
}
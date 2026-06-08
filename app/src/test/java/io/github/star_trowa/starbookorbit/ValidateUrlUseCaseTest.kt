package io.github.star_trowa.starbookorbit

import io.github.star_trowa.starbookorbit.domain.usecase.ValidateUrlUseCase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidateUrlUseCaseTest {

    private val useCase = ValidateUrlUseCase()

    @Test
    fun `blank input returns Invalid`() {
        val result = useCase("")
        assertIs<ValidateUrlUseCase.Result.Invalid>(result)
    }

    @Test
    fun `missing scheme returns Invalid`() {
        val result = useCase("192.168.1.5:8090")
        assertIs<ValidateUrlUseCase.Result.Invalid>(result)
    }

    @Test
    fun `http scheme returns Valid`() {
        val result = useCase("http://192.168.1.5:8090")
        assertIs<ValidateUrlUseCase.Result.Valid>(result)
    }

    @Test
    fun `https scheme returns Valid`() {
        val result = useCase("https://mybookorbit.ts.net")
        assertIs<ValidateUrlUseCase.Result.Valid>(result)
    }

    @Test
    fun `trailing slash is stripped in normalizedUrl`() {
        val result = useCase("http://192.168.1.5:8090/")
        val validResult = assertIs<ValidateUrlUseCase.Result.Valid>(result)
        assertEquals("http://192.168.1.5:8090", validResult.config.normalizedUrl)
    }

    @Test
    fun `scheme only with no host returns Invalid`() {
        val httpResult = useCase("http://")
        assertIs<ValidateUrlUseCase.Result.Invalid>(httpResult)

        val httpsResult = useCase("https://   ") // tests trailing spaces too
        assertIs<ValidateUrlUseCase.Result.Invalid>(httpsResult)
    }
}
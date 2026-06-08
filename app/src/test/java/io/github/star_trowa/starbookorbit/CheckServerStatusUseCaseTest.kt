package io.github.star_trowa.starbookorbit

import io.github.star_trowa.starbookorbit.domain.model.ServerConnectionResult
import io.github.star_trowa.starbookorbit.domain.model.ServerType
import io.github.star_trowa.starbookorbit.domain.usecase.CheckServerStatusUseCase
import io.github.star_trowa.starbookorbit.domain.usecase.ServerVerifier
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CheckServerStatusUseCaseTest {

    // A mock verifier we can easily control in our tests
    class FakeVerifier(
        override val serverType: ServerType = ServerType.BOOK_ORBIT,
        var shouldReturn: Boolean = true,
        var shouldThrow: Boolean = false
    ) : ServerVerifier {
        override suspend fun verify(baseUrl: String): Boolean {
            if (shouldThrow) throw IOException("Fake network timeout")
            return shouldReturn
        }
    }

    @Test
    fun `verify valid server returns Success`() = runTest {
        val fakeVerifier = FakeVerifier(shouldReturn = true)
        val useCase = CheckServerStatusUseCase(listOf(fakeVerifier))

        val result = useCase("http://192.168.1.5")

        val successResult = assertIs<ServerConnectionResult.Success>(result)
        assertEquals(ServerType.BOOK_ORBIT, successResult.type)
    }

    @Test
    fun `verify unknown server returns UnsupportedServer`() = runTest {
        val fakeVerifier = FakeVerifier(shouldReturn = false)
        val useCase = CheckServerStatusUseCase(listOf(fakeVerifier))

        val result = useCase("http://192.168.1.5")

        assertIs<ServerConnectionResult.UnsupportedServer>(result)
    }

    @Test
    fun `network timeout throws IOException returns Unreachable`() = runTest {
        val fakeVerifier = FakeVerifier(shouldThrow = true)
        val useCase = CheckServerStatusUseCase(listOf(fakeVerifier))

        val result = useCase("http://192.168.1.5")

        assertIs<ServerConnectionResult.Unreachable>(result)
    }
}
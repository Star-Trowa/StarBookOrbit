package io.github.star_trowa.starbookorbit

import app.cash.turbine.test
import io.github.star_trowa.starbookorbit.domain.model.ServerType
import io.github.star_trowa.starbookorbit.domain.usecase.CheckServerStatusUseCase
import io.github.star_trowa.starbookorbit.domain.usecase.ServerVerifier
import io.github.star_trowa.starbookorbit.presentation.reader.ReaderViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeSettingsRepository
    private lateinit var fakeVerifier: FakeVerifier
    private lateinit var viewModel: ReaderViewModel

    class FakeVerifier : ServerVerifier {
        override val serverType = ServerType.BOOK_ORBIT
        var shouldReturn = true
        var shouldThrow = false

        override suspend fun verify(baseUrl: String): Boolean {
            if (shouldThrow) throw IOException("Fake network error")
            return shouldReturn
        }
    }

    @Before
    fun setup() {
        fakeRepo = FakeSettingsRepository()
        fakeVerifier = FakeVerifier()
        // Inject the fake verifier into the real UseCase
        val checkServerStatusUseCase = CheckServerStatusUseCase(listOf(fakeVerifier))
        viewModel = ReaderViewModel(fakeRepo, checkServerStatusUseCase)
    }

    @Test
    fun `verifyServer success emits ServerUp`() = runTest {
        fakeVerifier.shouldReturn = true

        // Starts at Idle
        assertIs<ReaderViewModel.State.Idle>(viewModel.state.value)

        viewModel.verifyServer("http://192.168.1.5")

        // Finishes instantly. Check resting state.
        assertIs<ReaderViewModel.State.ServerUp>(viewModel.state.value)
    }

    @Test
    fun `verifyServer unreachable emits ServerDown`() = runTest {
        fakeVerifier.shouldThrow = true

        assertIs<ReaderViewModel.State.Idle>(viewModel.state.value)

        viewModel.verifyServer("http://192.168.1.5")

        assertIs<ReaderViewModel.State.ServerDown>(viewModel.state.value)
    }

    @Test
    fun `verifyServer unsupported emits ServerDown`() = runTest {
        fakeVerifier.shouldReturn = false

        assertIs<ReaderViewModel.State.Idle>(viewModel.state.value)

        viewModel.verifyServer("http://192.168.1.5")

        assertIs<ReaderViewModel.State.ServerDown>(viewModel.state.value)
    }

    @Test
    fun `disconnect clears repository and emits NavigateToSetup`() = runTest {
        viewModel.events.test {
            viewModel.disconnect()

            // Did it tell the Activity to navigate away?
            assertIs<ReaderViewModel.Event.NavigateToSetup>(awaitItem())

            // Did it successfully wipe the saved URL from disk?
            assertNull(fakeRepo.serverConfig.first())        }
    }
}
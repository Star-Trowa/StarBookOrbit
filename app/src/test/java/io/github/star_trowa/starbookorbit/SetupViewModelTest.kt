package io.github.star_trowa.starbookorbit

import app.cash.turbine.test
import io.github.star_trowa.starbookorbit.domain.model.ServerConfig
import io.github.star_trowa.starbookorbit.domain.usecase.ValidateUrlUseCase
import io.github.star_trowa.starbookorbit.presentation.setup.SetupViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeSettingsRepository()
    private val validateUrl = ValidateUrlUseCase()
    private lateinit var viewModel: SetupViewModel

    @Before
    fun setup() {
        viewModel = SetupViewModel(fakeRepo, validateUrl)
    }

    @Test
    fun `no existing config emits Idle state`() = runTest {
        // The FakeSettingsRepository is initialized with null by default,
        // so don't even need to call setInitialState().

        viewModel = SetupViewModel(fakeRepo, validateUrl)

        // Because using UnconfinedTestDispatcher, the init block coroutine
        // finishes instantly, leaving the ViewModel resting at Idle.
        assertIs<SetupViewModel.State.Idle>(viewModel.state.value)
    }

    @Test
    fun `existing config emits ExistingConfig state`() = runTest {
        fakeRepo.setInitialState(ServerConfig("http://192.168.1.5:8090"))
        viewModel = SetupViewModel(fakeRepo, validateUrl)

        // Just check the resting value
        assertIs<SetupViewModel.State.ExistingConfig>(viewModel.state.value)
    }

    @Test
    fun `invalid url emits Error state`() = runTest {
        viewModel.state.test {
            var state = awaitItem()
            if (state is SetupViewModel.State.CheckingExisting) {
                state = awaitItem()
            }
            assertIs<SetupViewModel.State.Idle>(state)

            viewModel.connect("not-a-url")
            val errorState = awaitItem()
            assertIs<SetupViewModel.State.Error>(errorState)
        }
    }

    @Test
    fun `valid url emits Ready state`() = runTest {
        // ViewModel starts at Idle (because fake is initially null)
        viewModel.connect("http://192.168.1.5:8090")

        // Because of UnconfinedTestDispatcher, the coroutine finishes instantly.
        // The resting state should now be Ready.
        assertIs<SetupViewModel.State.Ready>(viewModel.state.value)
    }
}
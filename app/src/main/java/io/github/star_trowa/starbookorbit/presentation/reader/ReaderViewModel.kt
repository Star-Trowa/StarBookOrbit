package io.github.star_trowa.starbookorbit.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.star_trowa.starbookorbit.domain.model.ServerConnectionResult
import io.github.star_trowa.starbookorbit.domain.model.ServerType
import io.github.star_trowa.starbookorbit.domain.repository.SettingsRepository
import io.github.star_trowa.starbookorbit.domain.usecase.CheckServerStatusUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val settingsRepository: SettingsRepository,
    private val checkServerStatus: CheckServerStatusUseCase
) : ViewModel() {

    companion object {
        fun factory(
            repo: SettingsRepository,
            checkServerStatusUseCase: CheckServerStatusUseCase
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ReaderViewModel(repo, checkServerStatusUseCase) as T
            }
        }
    }

    sealed interface Event {
        object NavigateToSetup : Event
    }

    sealed interface State {
        object Idle : State
        object Loading : State
        object ServerUp : State
        object ServerDown : State
    }

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun verifyServer(url: String) {
        viewModelScope.launch {
            _state.value = State.Loading

            when (val result = checkServerStatus(url)) {
                is ServerConnectionResult.Success -> {
                    _state.value = State.ServerUp
                }
                is ServerConnectionResult.Unreachable -> {
                    // You can optionally pass a specific error string to the State here later
                    _state.value = State.ServerDown
                }
                is ServerConnectionResult.UnsupportedServer -> {
                    // You can optionally pass "Unrecognized Server Type" to the State here later
                    _state.value = State.ServerDown
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            settingsRepository.clear()
            _events.emit(Event.NavigateToSetup)
        }
    }
}
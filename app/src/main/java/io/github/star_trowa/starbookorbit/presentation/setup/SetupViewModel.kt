package io.github.star_trowa.starbookorbit.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.star_trowa.starbookorbit.domain.repository.SettingsRepository
import io.github.star_trowa.starbookorbit.domain.model.ServerConfig
import io.github.star_trowa.starbookorbit.domain.usecase.ValidateUrlUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SetupViewModel(
    private val settingsRepository: SettingsRepository,
    private val validateUrl: ValidateUrlUseCase
) : ViewModel() {

    companion object {
        fun factory(
            repo: SettingsRepository,
            validateUrlUseCase: ValidateUrlUseCase
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SetupViewModel(repo, validateUrlUseCase) as T
            }
        }
    }

    sealed interface State {
        object Idle : State
        object CheckingExisting : State
        data class ExistingConfig(val config: ServerConfig) : State
        object Saving : State
        data class Error(val message: String) : State
        data class Ready(val config: ServerConfig) : State
    }

    private val _state = MutableStateFlow<State>(State.CheckingExisting)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.serverConfig.firstOrNull()?.let {
                _state.value = State.ExistingConfig(it)
            } ?: run {
                _state.value = State.Idle
            }
        }
    }

    fun connect(urlInput: String) {
        when (val result = validateUrl(urlInput)) {
            is ValidateUrlUseCase.Result.Invalid -> {
                _state.value = State.Error(result.reason)
            }
            is ValidateUrlUseCase.Result.Valid -> {
                viewModelScope.launch {
                    _state.value = State.Saving
                    settingsRepository.save(result.config)
                    _state.value = State.Ready(result.config)
                }
            }
        }
    }
}
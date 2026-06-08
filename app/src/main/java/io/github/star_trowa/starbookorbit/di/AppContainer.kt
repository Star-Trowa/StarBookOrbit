package io.github.star_trowa.starbookorbit.di

import android.content.Context
import io.github.star_trowa.starbookorbit.domain.repository.SettingsRepository
import io.github.star_trowa.starbookorbit.data.repository.SettingsRepositoryImpl
import io.github.star_trowa.starbookorbit.domain.usecase.CheckServerStatusUseCase
import io.github.star_trowa.starbookorbit.domain.usecase.ValidateUrlUseCase

class DefaultAppContainer(private val context: Context) {

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(context)
    }

    // Add your Use Cases to the DI Container!
    val checkServerStatusUseCase: CheckServerStatusUseCase by lazy {
        CheckServerStatusUseCase()
    }

    val validateUrlUseCase: ValidateUrlUseCase by lazy {
        ValidateUrlUseCase()
    }
}
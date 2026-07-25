package io.github.star_trowa.starbookorbit

import android.app.Application
import com.google.android.material.color.DynamicColors
import io.github.star_trowa.starbookorbit.di.DefaultAppContainer

class StarBookOrbitApp : Application() {

    lateinit var container: DefaultAppContainer

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this) // Enable MY
        container = DefaultAppContainer(this)
    }
}
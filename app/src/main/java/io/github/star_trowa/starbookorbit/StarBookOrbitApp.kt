package io.github.star_trowa.starbookorbit

import android.app.Application
import io.github.star_trowa.starbookorbit.di.DefaultAppContainer

class StarBookOrbitApp : Application() {

    lateinit var container: DefaultAppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
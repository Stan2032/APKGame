package io.github.stan2032.gravitygambit

import android.app.Application
import io.github.stan2032.gravitygambit.data.ProfileRepository

class GravityGambitApp : Application() {
    lateinit var profileRepo: ProfileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        profileRepo = ProfileRepository(this)
    }
}

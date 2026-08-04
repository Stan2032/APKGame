package io.github.stan2032.gravitygambit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.stan2032.gravitygambit.audio.SoundManager
import io.github.stan2032.gravitygambit.billing.BillingManager
import io.github.stan2032.gravitygambit.ui.App
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme

class MainActivity : ComponentActivity() {

    private lateinit var billing: BillingManager
    private lateinit var sound: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GravityGambitApp
        billing = BillingManager(application, app.profileRepo)
        billing.connect()
        sound = SoundManager(this)
        setContent {
            GravityGambitTheme {
                App(repo = app.profileRepo, billing = billing, sound = sound)
            }
        }
    }
}

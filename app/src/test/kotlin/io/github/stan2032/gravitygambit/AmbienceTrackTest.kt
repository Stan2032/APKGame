package io.github.stan2032.gravitygambit

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.audio.AmbienceTrack
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.core.SystemGenerator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `AmbienceTrack` claims in its KDoc that a device which will not give it an
 * `AudioTrack` costs the player their drone and nothing else. That is a claim
 * about behaviour under failure, so it is checked rather than asserted (D-3):
 * Robolectric's audio stack is exactly such a hostile device, and every call
 * below is made against it in orders a real session produces.
 *
 * Any escaping exception fails the test — that is the whole assertion.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AmbienceTrackTest {

    private fun spec(seed: Long) = SystemGenerator.generate(seed, 4, GameMode.CAMPAIGN)

    @Test
    fun aFullSessionOfCallsNeverThrows() {
        val t = AmbienceTrack()
        t.enabled = true
        t.setSystem(spec(Seeds.campaign(0, 0)))
        t.resyncPulsarPhase()
        t.pause()
        t.resume()
        t.enabled = false
        t.resyncPulsarPhase()
        t.enabled = true
        t.setSystem(spec(Seeds.campaign(1, 0)))   // new system mid-session
        t.setSystem(null)                          // back to a menu
        t.resume()                                 // resume with nothing loaded
        t.release()
    }

    @Test
    fun callsAfterReleaseAreStillSafe() {
        // The activity can be destroyed while a build coroutine is in flight;
        // a late callback must not take the process down with it.
        val t = AmbienceTrack()
        t.setSystem(spec(Seeds.campaign(2, 0)))
        t.release()
        t.pause()
        t.resume()
        t.resyncPulsarPhase()
        t.setSystem(spec(Seeds.campaign(3, 0)))
        t.release()
    }
}

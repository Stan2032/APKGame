package io.github.stan2032.gravitygambit.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.github.stan2032.gravitygambit.core.Ambience
import io.github.stan2032.gravitygambit.core.SystemSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays the pad that [Ambience] synthesizes for the current system.
 *
 * `MODE_STATIC` with hardware loop points, rather than a streaming writer
 * thread: the whole seven-second loop is handed to the audio HAL once and
 * repeated there, so a pad that plays for an hour costs the same CPU as one
 * that plays for a second — nothing (docs/PERFORMANCE.md). That is only
 * possible because the loop is seamless by construction; a loop with a seam
 * would have needed a crossfading streamer and a thread to run it.
 *
 * Every call into [AudioTrack] is guarded. A device that cannot allocate the
 * buffer, or an OEM that dislikes a seek, must cost the player their
 * background drone and nothing else — a game does not crash over ambience.
 */
class AmbienceTrack {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var track: AudioTrack? = null

    /** Which system the loaded loop belongs to; guards against rebuilding on recomposition. */
    @Volatile
    private var loadedKey: Long = NO_KEY

    @Volatile
    private var pulsarLocked = false

    @Volatile
    private var building: Job? = null

    /** True while the host screen is in the foreground and wants sound. */
    @Volatile
    private var wantsPlayback = false

    @Volatile
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) safely { track?.pause() } else if (wantsPlayback) startAudible()
        }

    /**
     * Point the pad at [spec], building it off the main thread. Passing null
     * stops and frees the track — menus are quiet on purpose, so that entering
     * a system is a change the player hears.
     */
    fun setSystem(spec: SystemSpec?) {
        if (spec == null) {
            wantsPlayback = false
            building?.cancel()
            loadedKey = NO_KEY
            releaseTrack()
            return
        }
        // The system is a pure function of its seed, and the pad reads only
        // the bodies and the objective, so this pair identifies the loop.
        val key = spec.seed * 31 + spec.objectiveId
        wantsPlayback = true
        if (key == loadedKey) {
            startAudible()
            return
        }
        building?.cancel()
        loadedKey = key
        pulsarLocked = Ambience.pulsarLocked(spec)
        building = scope.launch {
            val pcm = Ambience.loopFor(spec)
            if (!isActive || loadedKey != key) return@launch
            install(pcm)
        }
    }

    /**
     * Restart the loop at sample zero so a pulsar's tremolo is in phase with
     * the shot that is about to fly (see [Ambience.pulsarLocked]).
     *
     * A no-op without a pulsar, which is the common case: seeking costs a
     * pause/play pair the ear can sometimes catch, and there is nothing to
     * learn from locking a system that has no pulse to lock to.
     */
    fun resyncPulsarPhase() {
        if (!pulsarLocked || !enabled) return
        safely {
            val t = track ?: return@safely
            t.pause()
            t.setPlaybackHeadPosition(0)
            t.play()
        }
    }

    fun pause() {
        wantsPlayback = false
        safely { track?.pause() }
    }

    fun resume() {
        if (loadedKey == NO_KEY) return
        wantsPlayback = true
        startAudible()
    }

    fun release() {
        wantsPlayback = false
        building?.cancel()
        releaseTrack()
        scope.cancel()
    }

    private fun install(pcm: ShortArray) {
        releaseTrack()
        val built = safely {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Ambience.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * BYTES_PER_SAMPLE)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .also {
                    it.write(pcm, 0, pcm.size)
                    it.setLoopPoints(0, pcm.size, LOOP_FOREVER)
                    it.setVolume(0f)
                }
        } ?: return
        track = built
        if (wantsPlayback && enabled) {
            play()
            scope.launch { fadeIn(built) }
        }
    }

    private fun play() {
        safely { track?.takeIf { it.playState != AudioTrack.PLAYSTATE_PLAYING }?.play() }
    }

    /**
     * Resume at full level. A track installed while the screen was in the
     * background sits at volume zero from [install]; without this it would
     * play inaudibly forever after the player came back.
     */
    private fun startAudible() {
        play()
        safely { track?.setVolume(1f) }
    }

    /**
     * A drone that snaps to full level announces itself; one that arrives does
     * not. Six hundred milliseconds in twenty steps is below the rate at which
     * a level change is audible as steps.
     */
    private suspend fun fadeIn(t: AudioTrack) {
        for (step in 1..FADE_STEPS) {
            delay(FADE_MS / FADE_STEPS)
            if (track !== t) return
            safely { t.setVolume(step.toFloat() / FADE_STEPS) }
        }
    }

    private fun releaseTrack() {
        val t = track ?: return
        track = null
        safely {
            t.pause()
            t.release()
        }
    }

    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val NO_KEY = Long.MIN_VALUE
        const val BYTES_PER_SAMPLE = 2
        const val LOOP_FOREVER = -1
        const val FADE_MS = 600L
        const val FADE_STEPS = 20
    }
}

package io.github.stan2032.gravitygambit.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.github.stan2032.gravitygambit.R

/**
 * Thin SoundPool wrapper. All assets are synthesized by tools/gen_sfx.py
 * (owned math, no licenses). Loads once at app start; play() is fire-and-forget
 * and allocation-free (docs/PERFORMANCE.md). [enabled] mirrors the profile
 * setting; when off, play() is a no-op rather than muting streams.
 */
class SoundManager(context: Context) {

    enum class Sfx { FIRE, HIT_STATION, HIT_PLANET, BRAKE, STYLE, WIN, LOSE }

    @Volatile
    var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.FIRE to pool.load(context, R.raw.sfx_fire, 1),
        Sfx.HIT_STATION to pool.load(context, R.raw.sfx_hit_station, 1),
        Sfx.HIT_PLANET to pool.load(context, R.raw.sfx_hit_planet, 1),
        Sfx.BRAKE to pool.load(context, R.raw.sfx_brake, 1),
        Sfx.STYLE to pool.load(context, R.raw.sfx_style, 1),
        Sfx.WIN to pool.load(context, R.raw.sfx_win, 1),
        Sfx.LOSE to pool.load(context, R.raw.sfx_lose, 1),
    )

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!enabled) return
        val id = ids[sfx] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }
}

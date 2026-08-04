package io.github.stan2032.gravitygambit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.stan2032.gravitygambit.core.Economy
import io.github.stan2032.gravitygambit.core.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "gravity_gambit")

/**
 * Single source of truth for the player profile. All writes funnel through
 * [update] so state transitions stay atomic; reward math lives in core Economy.
 */
class ProfileRepository(private val context: Context) {
    private val key = stringPreferencesKey("profile_json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val profile: StateFlow<Profile> = context.dataStore.data
        .map { prefs -> prefs[key]?.let { Profile.fromJson(it) } ?: Profile() }
        .stateIn(scope, SharingStarted.Eagerly, Profile())

    fun update(transform: (Profile) -> Profile) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val current = prefs[key]?.let { Profile.fromJson(it) } ?: Profile()
                prefs[key] = transform(current).toJson()
            }
        }
    }

    /** Returns stardust earned (for the summary UI); applies stars + reward atomically. */
    fun recordCampaign(sector: Int, level: Int, difficulty: Int, stars: Int): Int {
        if (stars <= 0) return 0
        val snapshot = profile.value
        val mapKey = "$sector:$level"
        val prev = snapshot.campaignStars[mapKey] ?: 0
        val earned = if (prev == 0) {
            Economy.campaignReward(difficulty, stars, firstClear = true)
        } else if (stars > prev) {
            10 * (stars - prev)
        } else {
            Economy.campaignReward(difficulty, stars, firstClear = false)
        }
        update { p ->
            val old = p.campaignStars[mapKey] ?: 0
            p.copy(
                stardust = p.stardust + earned,
                campaignStars = p.campaignStars + (mapKey to maxOf(old, stars)),
            )
        }
        return earned
    }

    /** Returns stardust earned. Streak counts consecutive UTC days played. */
    fun recordDaily(epochDay: Long, score: Int): Int {
        val snapshot = profile.value
        val firstPlayToday = snapshot.lastDailyDay != epochDay
        val earned = if (firstPlayToday) Economy.dailyReward(score) else 0
        update { p ->
            val dayKey = epochDay.toString()
            val best = maxOf(p.dailyBest[dayKey] ?: 0, score)
            val streak = when {
                p.lastDailyDay == epochDay -> p.dailyStreak
                p.lastDailyDay == epochDay - 1 -> p.dailyStreak + 1
                else -> 1
            }
            p.copy(
                stardust = p.stardust + earned,
                dailyBest = p.dailyBest + (dayKey to best),
                dailyStreak = streak,
                lastDailyDay = epochDay,
            )
        }
        return earned
    }

    /** Returns stardust earned. Hot-seat duels award nothing (self-farm guard). */
    fun recordDuel(win: Boolean, codeDuel: Boolean): Int {
        if (!codeDuel) return 0
        val earned = Economy.duelReward(win, codeDuel = true)
        update { p ->
            p.copy(
                stardust = p.stardust + earned,
                duelWins = p.duelWins + (if (win) 1 else 0),
                duelLosses = p.duelLosses + (if (win) 0 else 1),
            )
        }
        return earned
    }

    /** Returns true if the spend was authorized against the current snapshot. */
    fun spend(cost: Int, alsoApply: (Profile) -> Profile): Boolean {
        if (profile.value.stardust < cost) return false
        update { p ->
            if (p.stardust < cost) p else alsoApply(p.copy(stardust = p.stardust - cost))
        }
        return true
    }
}

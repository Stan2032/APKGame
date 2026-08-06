package io.github.stan2032.gravitygambit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.stan2032.gravitygambit.core.AdRewards
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

    /** Ad Pass holders earn multiplied match rewards (docs/ADS.md). */
    private fun withPassMultiplier(base: Int): Int =
        if (profile.value.adPassActive) base * AdRewards.PASS_REWARD_MULTIPLIER else base

    /** Returns stardust earned (for the summary UI); applies stars + reward atomically. */
    fun recordCampaign(sector: Int, level: Int, difficulty: Int, stars: Int): Int {
        if (stars <= 0) return 0
        val snapshot = profile.value
        val mapKey = "$sector:$level"
        val prev = snapshot.campaignStars[mapKey] ?: 0
        val base = if (prev == 0) {
            Economy.campaignReward(difficulty, stars, firstClear = true)
        } else if (stars > prev) {
            10 * (stars - prev)
        } else {
            Economy.campaignReward(difficulty, stars, firstClear = false)
        }
        val earned = withPassMultiplier(base)
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
        // Pay for the streak this run *establishes*, not the one it started
        // from, so the multiplier matches the number the player is shown. It
        // cannot be farmed: a streak advances at most once per UTC day, and
        // only the first play of a day pays at all.
        val newStreak =
            Economy.dailyStreakAfter(snapshot.lastDailyDay, snapshot.dailyStreak, epochDay)
        val earned =
            if (firstPlayToday) {
                withPassMultiplier(Economy.dailyReward(score, newStreak))
            } else {
                0
            }
        update { p ->
            val dayKey = epochDay.toString()
            val best = maxOf(p.dailyBest[dayKey] ?: 0, score)
            val streak = Economy.dailyStreakAfter(p.lastDailyDay, p.dailyStreak, epochDay)
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
        val earned = withPassMultiplier(Economy.duelReward(win, codeDuel = true))
        update { p ->
            p.copy(
                stardust = p.stardust + earned,
                duelWins = p.duelWins + (if (win) 1 else 0),
                duelLosses = p.duelLosses + (if (win) 0 else 1),
            )
        }
        return earned
    }

    /**
     * Grants the opt-in rewarded-ad payout, respecting the daily cap.
     * Returns the stardust granted (0 if capped). Called only after the ad
     * provider confirms the reward was earned.
     */
    fun recordAdWatch(epochDay: Long): Int {
        val snapshot = profile.value
        val watchesToday = if (snapshot.lastAdWatchDay == epochDay) snapshot.adWatchesToday else 0
        if (watchesToday >= AdRewards.DAILY_WATCH_CAP) return 0
        update { p ->
            val current = if (p.lastAdWatchDay == epochDay) p.adWatchesToday else 0
            if (current >= AdRewards.DAILY_WATCH_CAP) p
            else p.copy(
                stardust = p.stardust + AdRewards.WATCH_STARDUST,
                adWatchesToday = current + 1,
                lastAdWatchDay = epochDay,
            )
        }
        return AdRewards.WATCH_STARDUST
    }

    /** Ad Pass daily drop; call on app open. Returns the bonus granted (0 if already claimed/inactive). */
    fun claimPassDailyBonus(epochDay: Long): Int {
        val snapshot = profile.value
        if (!snapshot.adPassActive || snapshot.lastPassBonusDay == epochDay) return 0
        update { p ->
            if (!p.adPassActive || p.lastPassBonusDay == epochDay) p
            else p.copy(stardust = p.stardust + AdRewards.PASS_DAILY_BONUS, lastPassBonusDay = epochDay)
        }
        return AdRewards.PASS_DAILY_BONUS
    }

    /** Called by billing with the reconciled subscription state. */
    fun setAdPassActive(active: Boolean) {
        if (profile.value.adPassActive != active) {
            update { it.copy(adPassActive = active) }
        }
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

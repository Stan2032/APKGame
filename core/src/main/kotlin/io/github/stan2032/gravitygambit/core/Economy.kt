package io.github.stan2032.gravitygambit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Player profile — the entire save state, serialized to JSON in DataStore.
 * Everything is earnable in-game; IAP only accelerates cosmetics. That keeps
 * PVP fair, players happy, and the Play Data-Safety story trivial.
 */
@Serializable
data class Profile(
    val stardust: Int = 0,
    val campaignStars: Map<String, Int> = emptyMap(),   // "sector:level" -> best stars (1..3)
    val unlockedProjectiles: List<Int> = listOf(0),
    val ownedTrails: List<String> = listOf("classic"),
    val selectedTrail: String = "classic",
    val previewLevel: Int = 0,                          // aim-preview upgrade tier: 0,1,2
    val dailyBest: Map<String, Int> = emptyMap(),       // epochDay -> best score
    val dailyStreak: Int = 0,
    val lastDailyDay: Long = 0,
    val duelWins: Int = 0,
    val duelLosses: Int = 0,
    val hapticsEnabled: Boolean = true,
    val lastDuelCode: String = "",
) {
    val totalStars: Int get() = campaignStars.values.sum()

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(s: String): Profile = try {
            json.decodeFromString(serializer(), s)
        } catch (_: Exception) {
            Profile()
        }
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    fun starsFor(sector: Int, level: Int): Int = campaignStars["$sector:$level"] ?: 0
}

object Economy {

    class TrailDef(val id: String, val name: String, val cost: Int, val colors: List<Long>)

    val TRAILS = listOf(
        TrailDef("classic", "Classic", 0, listOf(0xFFB0BEC5, 0xFF78909C)),
        TrailDef("aurora", "Aurora", 300, listOf(0xFF69F0AE, 0xFF00E5FF)),
        TrailDef("ember", "Ember", 300, listOf(0xFFFFAB40, 0xFFFF5252)),
        TrailDef("quantum", "Quantum", 600, listOf(0xFFE040FB, 0xFF40C4FF)),
        TrailDef("prism", "Prism", 900, listOf(0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFFE040FB)),
    )

    fun trail(id: String): TrailDef = TRAILS.firstOrNull { it.id == id } ?: TRAILS[0]

    class ProjectileUnlock(val typeId: Int, val starsRequired: Int, val cost: Int)

    val PROJECTILE_UNLOCKS = listOf(
        ProjectileUnlock(ProjectileType.HEAVY.id, starsRequired = 12, cost = 250),
        ProjectileUnlock(ProjectileType.SPLITTER.id, starsRequired = 30, cost = 600),
        ProjectileUnlock(ProjectileType.RETRO.id, starsRequired = 50, cost = 900),
    )

    /** Cost to move preview tier 0->1 and 1->2. */
    val PREVIEW_UPGRADE_COSTS = listOf(250, 600)

    fun previewTicks(level: Int): Int = when (level) {
        0 -> 90
        1 -> 180
        else -> 270
    }

    fun campaignReward(difficulty: Int, stars: Int, firstClear: Boolean): Int =
        if (firstClear) 25 + 10 * difficulty + 10 * stars else 5 + 2 * stars

    fun dailyReward(score: Int): Int = minOf(60, score / 10)

    fun duelReward(win: Boolean, codeDuel: Boolean): Int = when {
        !win -> 10
        codeDuel -> 60
        else -> 40
    }

    /** Sector s unlocks when the previous sector has at least this many stars (of 36). */
    const val SECTOR_UNLOCK_STARS = 20
}

/**
 * Play Billing product IDs. Prices are configured in Play Console (suggested:
 * $0.99 / $2.99 / $2.49 — deliberately cheap; see docs/MONETIZATION.md).
 */
object Iap {
    const val STARDUST_SMALL = "stardust_small"     // consumable: +600 stardust
    const val STARDUST_LARGE = "stardust_large"     // consumable: +2200 stardust
    const val COSMIC_PACK = "cosmic_pack"           // entitlement: all cosmetic trails

    val CONSUMABLE_GRANTS = mapOf(STARDUST_SMALL to 600, STARDUST_LARGE to 2200)
    val ALL_PRODUCT_IDS = listOf(STARDUST_SMALL, STARDUST_LARGE, COSMIC_PACK)
}

package io.github.stan2032.gravitygambit.ui.game

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.ads.RewardedAdsProvider
import io.github.stan2032.gravitygambit.audio.AmbienceTrack
import io.github.stan2032.gravitygambit.audio.SoundManager
import io.github.stan2032.gravitygambit.core.Economy
import io.github.stan2032.gravitygambit.core.Expedition
import io.github.stan2032.gravitygambit.core.Gambit
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Objective
import io.github.stan2032.gravitygambit.core.Preview
import io.github.stan2032.gravitygambit.core.ProjectileType
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.core.Vec2
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.ui.AccentCyan
import io.github.stan2032.gravitygambit.ui.BgColor
import io.github.stan2032.gravitygambit.ui.DangerRed
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.OkGreen
import io.github.stan2032.gravitygambit.ui.PanelColor
import io.github.stan2032.gravitygambit.ui.StarYellow
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.TextPrimary
import io.github.stan2032.gravitygambit.ui.animationsEnabled
import io.github.stan2032.gravitygambit.ui.ShareMessages
import io.github.stan2032.gravitygambit.ui.screens.FeedbackDialog
import io.github.stan2032.gravitygambit.ui.shareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun GameScreen(
    launch: GameLaunch,
    profile: Profile,
    repo: ProfileRepository,
    ads: RewardedAdsProvider,
    sound: SoundManager,
    onNavigate: (Nav) -> Unit,
    ambience: AmbienceTrack = remember { AmbienceTrack() },
    /** Reports an Expedition leg's outcome to the run that owns it (D-34). */
    onLegFinished: (shots: Int, stars: Int, cleared: Boolean) -> Unit = { _, _, _ -> },
) {
    var controller by remember(launch) { mutableStateOf<MatchController?>(null) }
    var failed by remember(launch) { mutableStateOf(false) }

    LaunchedEffect(launch) {
        val created = withContext(Dispatchers.Default) { MatchController.create(launch) }
        if (created == null) failed = true else controller = created
    }

    val exitTarget: Nav = when {
        // A leg exits to the junction it came from, never to the star map.
        launch.expeditionLeg != null -> Nav.Expedition
        else -> when (launch.mode) {
        GameMode.CAMPAIGN -> Nav.Campaign
        GameMode.DAILY -> Nav.Home
        GameMode.DUEL -> Nav.Duel
        }
    }

    when {
        failed -> CenteredMessage(
            "That challenge code failed verification.",
            "It was corrupted in transit — or someone tried to cheat. The physics noticed.",
        ) { onNavigate(exitTarget) }
        controller == null -> {
            BackHandler { onNavigate(exitTarget) }
            CenteredMessage("Charting the system…", "solving gravity, guaranteeing a route", null)
        }
        else -> MatchUI(controller!!, profile, repo, ads, sound, ambience, exitTarget, onNavigate, onLegFinished)
    }
}

@Composable
private fun CenteredMessage(title: String, sub: String, onBack: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().background(BgColor).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (onBack == null) CircularProgressIndicator(color = AccentCyan)
        Spacer(Modifier.height(18.dp))
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
        if (onBack != null) {
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun MatchUI(
    c: MatchController,
    profile: Profile,
    repo: ProfileRepository,
    ads: RewardedAdsProvider,
    sound: SoundManager,
    ambience: AmbienceTrack,
    exitTarget: Nav,
    onNavigate: (Nav) -> Unit,
    onLegFinished: (shots: Int, stars: Int, cleared: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val motionOk = animationsEnabled()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showQuit by remember { mutableStateOf(false) }
    var timeSec by remember { mutableStateOf(0f) }

    // The pad is a property of the system, so it is set from the system and
    // not from the screen: a rematch on the same seed keeps playing without a
    // rebuild, and a new seed rebuilds off the main thread (AmbienceTrack).
    LaunchedEffect(c) { ambience.setSystem(c.spec) }

    // Global clock for ambient animation (twinkle, pulses).
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now -> timeSec = (now - start) / 1e9f }
        }
    }

    // Live simulation pacing + resolve animation.
    LaunchedEffect(c, c.phase) {
        when (c.phase) {
            Phase.FLYING -> {
                sound.play(SoundManager.Sfx.FIRE, volume = if (c.isReplaying) 0.7f else 1f)
                // Restart the pad at sample zero so a pulsar's tremolo tracks
                // the pull the shot is actually flying through. No-op without a
                // pulsar; the launch report covers the seek where there is one.
                ambience.resyncPulsarPhase()
                // Time-based pacing: ticks derive from wall clock, not frame
                // count, so playback speed is identical on 60/90/120Hz panels.
                var startNanos = -1L
                var ticksDone = 0
                while (isActive && c.phase == Phase.FLYING) {
                    val now = withFrameNanos { it }
                    if (startNanos < 0) startNanos = now
                    val target = ((now - startNanos) * MatchController.PLAYBACK_TICKS_PER_SEC / 1_000_000_000L).toInt() + 1
                    val toRun = (target - ticksDone).coerceIn(0, MatchController.MAX_TICKS_PER_FRAME)
                    if (toRun > 0) {
                        c.advanceBy(toRun)
                        ticksDone += toRun
                    }
                }
            }
            Phase.RESOLVING -> {
                val res = c.lastResult
                val hit = res?.stationHits?.isNotEmpty() == true
                if (hit && profile.hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                when {
                    hit -> sound.play(SoundManager.Sfx.HIT_STATION)
                    (res?.bodyHitCount ?: 0) > 0 -> sound.play(SoundManager.Sfx.HIT_PLANET, 0.8f)
                }
                if (res != null && res.styleBonus > 0) sound.play(SoundManager.Sfx.STYLE, 0.6f)
                if (c.explosion != null) {
                    val start = withFrameNanos { it }
                    var p = 0f
                    while (isActive && p < 1f) {
                        val elapsed = (withFrameNanos { it } - start) / 1e9f
                        // Hit-stop: hold the first frames still. A beat of
                        // stillness on impact is the cheapest way to make a hit
                        // read as a hit rather than as an animation starting.
                        p = if (elapsed < Explosion.HIT_STOP_SECONDS) 0f
                        else ((elapsed - Explosion.HIT_STOP_SECONDS) / 0.7f).coerceAtMost(1f)
                        c.explosionProgress = p
                    }
                }
                c.postResolve()
            }
            else -> Unit
        }
    }

    // Apply rewards exactly once when the match ends.
    LaunchedEffect(c.phase) {
        if (c.phase == Phase.OVER) {
            c.summary?.let { sound.play(if (it.win) SoundManager.Sfx.WIN else SoundManager.Sfx.LOSE, 0.8f) }
        }
        if (c.phase == Phase.OVER && !c.rewardsApplied) {
            c.rewardsApplied = true
            val s = c.summary ?: return@LaunchedEffect
            // Season XP rides on the same handshake as stardust, so a match
            // can never pay one and not the other.
            val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()
            // Baseline through the rollover: the first match of a new season
            // starts from that season's zero, not from last season's total.
            val xpBefore = profile.rolledIntoSeason(today).seasonXp
            val leg = c.launch.expeditionLeg
            c.dustEarned = if (leg != null) {
                // An Expedition leg pays nothing on its own: the run banks
                // once, at the end (D-34). Paying per leg would make a run
                // strictly better than the campaign for the same systems while
                // also being the thing that risks them, and would write
                // campaign stars for a sector the player never visited.
                onLegFinished(c.engine.shotsUsed, s.stars, s.win)
                0
            } else when (c.launch.mode) {
                GameMode.CAMPAIGN -> repo.recordCampaign(
                    c.launch.sector, c.launch.level, c.launch.difficulty, s.stars,
                    epochDay = today,
                    gambitsKept = c.engine.gambitKept.indices.count { i ->
                        c.engine.gambitKept[i] &&
                            c.engine.results[i].input.gambitId != Gambit.NONE.id
                    },
                    objectiveMet = c.engine.objectiveMet,
                )
                GameMode.DAILY -> repo.recordDaily(c.launch.epochDay, s.score)
                GameMode.DUEL -> {
                    val winner = c.engine.duelWinner()
                    if (!c.launch.hotseat && winner != null) {
                        repo.recordDuel(
                            win = winner == c.localPlayer, codeDuel = true, epochDay = today,
                        )
                    } else 0
                }
            }
            // Derived rather than plumbed back through three record methods:
            // the repository already wrote it, so read the difference.
            c.seasonXpEarned = (repo.profile.value.seasonXp - xpBefore).coerceAtLeast(0)
        }
    }

    // Keep the latest outgoing duel code recoverable from the Duel menu.
    LaunchedEffect(c.duelCode) {
        val code = c.duelCode ?: return@LaunchedEffect
        repo.update { it.copy(lastDuelCode = code) }
    }

    BackHandler {
        if (c.phase == Phase.OVER) onNavigate(exitTarget) else showQuit = true
    }

    val tr = remember(canvasSize) {
        if (canvasSize.width > 0) WorldTransform(canvasSize.width.toFloat(), canvasSize.height.toFloat()) else null
    }

    // Duels issue the top preview to both players; elsewhere it is earned —
    // and a Dark run takes it away for one leg (D-23, D-34).
    //
    // Blind practice is checked FIRST and beats everything, including a duel's
    // guaranteed top tier. That ordering is the point: it is a self-imposed
    // handicap the player switched on, so it must not be overridden by a rule
    // whose purpose is to stop anyone being *disadvantaged*. It pays nothing,
    // so choosing it can only cost you (D-23).
    val previewTicks = Economy.previewTicks(
        when {
            profile.blindPractice -> Economy.PREVIEW_NONE
            c.launch.mode == GameMode.DUEL -> Economy.MAX_PREVIEW_LEVEL
            c.launch.expeditionLeg != null ->
                Expedition.previewLevelFor(c.launch.expeditionLeg, profile.previewLevel)
            else -> profile.previewLevel
        }
    )
    val anchorSig = c.anchors.joinToString { "${'$'}{it.x},${'$'}{it.y},${'$'}{it.attract}" }
    // Wrecks are part of the board the preview must fly, or the line drawn on
    // screen is a trajectory through a field the shot will not meet — the
    // preview would be quietly lying about the one thing the player is using
    // it to judge (VISION §3.3).
    val previewBodies = remember(c.spec, c.engine.debris.size) {
        if (c.engine.debris.isEmpty()) c.spec.bodies else c.spec.bodies + c.engine.debris
    }
    val previewPaths = remember(
        c.aimAngleDeci, c.aimPower, c.selectedType, c.hpTick, c.phase, anchorSig, previewBodies,
        previewTicks,
    ) {
        // No trace means none: skip the simulation entirely rather than run it
        // for zero ticks, so blind really is blind and costs nothing to be.
        if (previewTicks > 0 && c.phase == Phase.AIMING && c.isMyTurn) {
            Preview.trajectories(
                previewBodies, c.engine.stations, c.engine.currentShooter,
                c.aimAngleDeci, c.aimPower, c.selectedType, previewTicks, c.anchors.toList(),
            )
        } else emptyList()
    }

    val trailColors = remember(profile.selectedTrail, c.isReplaying) {
        val def = if (c.isReplaying) Economy.trail("classic") else Economy.trail(profile.selectedTrail)
        def.colors.map { Color(it) }
    }
    val trailLut = remember(trailColors) { RenderCache.buildTrailLut(trailColors) }
    // Everything derivable from (system, canvas size) is precomputed once.
    val cache = remember(c.spec, tr) { tr?.let { RenderCache(c.spec, it) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(BgColor)) {
        // The world board is portrait (1000x1600). On a wide screen it
        // letterboxes to a narrow column, leaving ~65% of the display as empty
        // bars with the HUD floating over the play area. On wide layouts we
        // move the HUD into those bars instead: info on the left, controls on
        // the right, world untouched in the middle.
        val wide = maxWidth > maxHeight * 1.15f
        val sideWidth = if (wide) (maxWidth - maxHeight * 0.625f) / 2f else 0.dp

        // The world is a Canvas, so screen readers get nothing by default.
        // This description keeps the match legible to TalkBack users even
        // though aiming itself is a visual/gestural act.
        val worldDescription = when {
            c.phase == Phase.FLYING && c.isReplaying -> "Opponent's shot replaying"
            c.phase == Phase.FLYING -> "Shot in flight"
            c.phase == Phase.AIMING && c.isMyTurn ->
                "Star system ${c.spec.name}. Aiming at " +
                    "${"%.1f".format(c.aimAngleDeci / 10.0)} degrees, " +
                    "${c.aimPower / 10} percent power. " +
                    if (c.launch.mode == GameMode.DUEL) {
                        "Your station ${c.engine.hp[c.localPlayer]} health, " +
                            "opponent ${c.engine.hp[1 - c.localPlayer]} health."
                    } else {
                        "Shot ${c.engine.shotsUsed + 1}."
                    }
            else -> "Star system ${c.spec.name}"
        }

        // Portrait reserves rows for the HUD and controls instead of floating
        // them over the board: on tall phones the world letterboxes to less
        // than the screen height, and overlaying meant the controls covered
        // play area. The canvas takes the space that is actually left.
        val topReserve = if (wide) 0.dp else 62.dp
        val bottomReserve = if (wide) 0.dp else 196.dp

        Canvas(
            Modifier
                .fillMaxSize()
                .padding(top = topReserve, bottom = bottomReserve)
                .semantics { contentDescription = worldDescription }
                .onSizeChanged { canvasSize = it }
                .pointerInput(c, c.placingAnchors) {
                    detectTapGestures { pos ->
                        if (c.phase == Phase.AIMING && c.isMyTurn && c.placingAnchors) {
                            tr?.let { c.tapAnchor(it.toWorld(pos)) }
                        }
                    }
                }
                .pointerInput(c, c.placingAnchors) {
                    // Hand-rolled rather than detectDragGestures: that reports
                    // its origin only *after* touch slop is exceeded, which
                    // would plant the stick a few pixels from the finger and
                    // delay it appearing. Here the ring is centred on the
                    // actual touch-down and is visible from the first frame.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (c.placingAnchors || c.phase != Phase.AIMING || !c.isMyTurn) {
                            return@awaitEachGesture
                        }
                        val t0 = tr ?: return@awaitEachGesture
                        c.beginAimDrag(t0.toWorld(down.position))
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes
                                    .firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                c.updateAimDrag(t0.toWorld(change.position))
                                change.consume()
                            }
                        } finally {
                            c.endAimDrag()
                        }
                    }
                }
        ) {
            val t = tr ?: return@Canvas
            val cch = cache ?: return@Canvas
            // timeSec read here = draw-phase-only invalidation for ambience.
            val time = timeSec
            // The board shakes; the HUD does not. Shaking the readouts would
            // make the numbers unreadable at exactly the moment they matter.
            // Shake is decoration, so it obeys the system's animation setting
            // (ui/Motion.kt). A player who has turned animations off for
            // motion sensitivity still sees the explosion, the trail and every
            // number — they just do not get the board thrown around.
            val ex = if (motionOk) c.explosion else null
            val sx = if (ex == null) 0f else t.r(ex.shakeX(c.explosionProgress))
            val sy = if (ex == null) 0f else t.r(ex.shakeY(c.explosionProgress))
            translate(sx, sy) {
            drawSpaceBackdrop(cch, time)
            drawBodies(cch, time)
            // Comets drift, so they draw at their live simulation position —
            // exactly where the physics says they are.
            drawMovingBodies(c.spec, cch, c.liveShot?.tick ?: 0, time, t)
            // Wrecks from this match's own misses. Not in the cache: the seed
            // did not author them, the match did (VISION §3.3).
            if (c.engine.debris.isNotEmpty()) drawDebris(c.engine.debris, t, cch)
            if (c.spec.gates.isNotEmpty()) {
                drawGates(c.spec.gates, c.gatesPassed, time, t, cch)
            }
            if (c.spec.markedBody >= 0) {
                val marked = c.spec.bodies[c.spec.markedBody]
                val tick = c.liveShot?.tick ?: 0
                when (c.objective) {
                    Objective.CAPTURE ->
                        drawCaptureRing(marked, tick, c.captureProgress ?: 0f, time, t, cch)
                    Objective.COURIER -> drawMarkedTarget(marked, tick, time, t, cch)
                    else -> Unit
                }
            }
            drawStations(c.engine.stations, c.engine.hp, c.launch.mode, time, t, cch)
            if (c.phase == Phase.AIMING && c.isMyTurn) {
                drawPreview(previewPaths, t)
            }
            drawAnchors(c.anchors, c.placingAnchors, time, t, cch)
            // The floating joystick, drawn where the finger actually is.
            val dragFrom = c.dragOrigin
            val dragTo = c.dragCurrent
            if (dragFrom != null && dragTo != null) {
                drawAimStick(dragFrom, dragTo, c.aimPower, t, cch)
            }
            if (c.trailVersion >= 0) { // draw-phase dependency on trail mutations
                drawTrail(c.trail, trailLut, t)
            }
            c.liveShot?.let { shot ->
                if (!shot.done) drawProjectilesGlow(shot::visitProjectiles, t)
            }
            c.explosion?.let { drawExplosion(it, c.explosionProgress, t, cch) }
            }
            drawVignette(cch)
            if (c.phase == Phase.AIMING && c.isMyTurn) {
                drawAim(
                    c.engine.stations[c.engine.currentShooter],
                    c.aimAngleDeci, c.aimPower,
                    if (c.engine.currentShooter == 0) AccentCyan else Color(0xFFFF8A65),
                    t, cch,
                )
            }
        }

        if (wide) {
            // Left bar: match state. Right bar: controls. Neither overlaps play.
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(sideWidth)
                    .fillMaxHeight()
                    .safeDrawingPadding()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) { HudContent(c) }
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(sideWidth)
                    .fillMaxHeight()
                    .safeDrawingPadding()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) { ControlsContent(c, profile, sound) }
        } else {
            Column(Modifier.align(Alignment.TopCenter)) { TopHud(c) }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(min = bottomReserve)
                    .safeDrawingPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Bottom,
            ) { ControlsContent(c, profile, sound) }
        }

        // One-time coaching line for the very first level of a fresh profile.
        if (c.launch.mode == GameMode.CAMPAIGN && c.launch.sector == 0 && c.launch.level == 0 &&
            profile.totalStars == 0 && c.engine.shotsUsed == 0 && c.phase == Phase.AIMING
        ) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                Text(
                    "Drag anywhere to aim — pull further for more power.\nGravity bends every shot. Then press FIRE.",
                    color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .background(PanelColor.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }

        if (c.phase == Phase.HANDOFF) {
            HandoffOverlay(c, onShare = { text -> shareText(context, text) },
                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                onExit = { onNavigate(exitTarget) })
        }
        c.summary?.let { s ->
            if (c.phase == Phase.OVER) {
                SummaryOverlay(
                    c, s, profile, repo, ads,
                    onShare = { text -> shareText(context, text) },
                    onNavigate = onNavigate,
                    exitTarget = exitTarget,
                )
            }
        }
        if (showQuit) {
            AlertDialog(
                onDismissRequest = { showQuit = false },
                title = { Text("Abandon match?") },
                text = { Text("Progress in this match will be lost.") },
                confirmButton = {
                    Button(onClick = { showQuit = false; onNavigate(exitTarget) }) { Text("Leave") }
                },
                dismissButton = { TextButton(onClick = { showQuit = false }) { Text("Stay") } },
            )
        }
    }
}

@Composable
private fun TopHud(c: MatchController) {
    Column(
        Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 6.dp)
    ) { HudContent(c) }
}

/**
 * HUD body, shared by the portrait overlay and the wide-screen side bar.
 * In the side bar the parent Column is narrow, so the Row below wraps
 * naturally into stacked lines.
 */
@Composable
private fun ColumnScope.HudContent(c: MatchController) {
    // Read hpTick so HP text refreshes after every commit.
    val hpTick = c.hpTick
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (c.launch.mode) {
                        GameMode.CAMPAIGN -> "Sector ${c.launch.sector + 1} · Level ${c.launch.level + 1}"
                        GameMode.DAILY -> "Daily #${Seeds.dailyNumber(c.launch.epochDay)}"
                        GameMode.DUEL -> if (c.launch.hotseat) "Hot-seat duel" else "Code duel"
                    },
                    color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(c.spec.name, color = TextDim, fontSize = 12.sp)
            }
            when (c.launch.mode) {
                GameMode.CAMPAIGN -> Text(
                    "Shot ${(c.engine.shotsUsed + 1).coerceAtMost(Tuning.CAMPAIGN_SHOTS)}/${Tuning.CAMPAIGN_SHOTS}",
                    color = TextPrimary, fontSize = 14.sp,
                )
                GameMode.DAILY -> Text(
                    "✦ ${c.engine.score} · ${(c.engine.shotsUsed + 1).coerceAtMost(Tuning.DAILY_SHOTS)}/${Tuning.DAILY_SHOTS}",
                    color = TextPrimary, fontSize = 14.sp,
                )
                GameMode.DUEL -> Column(horizontalAlignment = Alignment.End) {
                    HpText("P1", c.engine.hp[0], AccentCyan, c.engine.currentShooter == 0 && c.phase == Phase.AIMING)
                    HpText("P2", c.engine.hp[1], Color(0xFFFF8A65), c.engine.currentShooter == 1 && c.phase == Phase.AIMING)
                }
            }
        }
        if (c.phase == Phase.FLYING && c.isReplaying) {
            Spacer(Modifier.height(8.dp))
            Text(
                "⚔️ Incoming — opponent's shot, verified & replayed",
                color = StarYellow, fontSize = 13.sp,
                modifier = Modifier
                    .background(PanelColor.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (c.objective != Objective.BREACH) {
            Spacer(Modifier.height(6.dp))
            val progress = c.captureProgress ?: 0f
            Text(
                when {
                    c.phase == Phase.FLYING && progress > 0f ->
                        "Holding orbit… ${(progress * 100).toInt()}%"
                    c.objective == Objective.THREAD && c.gatesPassed > 0 ->
                        "Gate ${c.gatesPassed} of ${c.spec.gates.size}"
                    else -> c.objective.brief
                },
                color = if (progress > 0f || c.gatesPassed > 0) StarYellow else AccentCyan,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(PanelColor.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        c.lastResult?.let { res ->
            if (c.phase == Phase.RESOLVING) {
                // The verdict on a declaration comes first: it is the thing the
                // player staked the shot on, and if it broke, the hit they just
                // watched land did not count. Saying so is not optional.
                c.lastGambitKept?.let { kept ->
                    val gambit = Gambit.byId(res.input.gambitId)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (kept) {
                            "${gambit.displayName} held  ${payoutLabel(gambit)}"
                        } else {
                            "${gambit.displayName} broken — shot wasted"
                        },
                        color = if (kept) OkGreen else DangerRed,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(PanelColor.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                if (res.styleNotes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        res.styleNotes.joinToString("  ·  "),
                        color = StarYellow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(PanelColor.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HpText(label: String, hp: Int, color: Color, active: Boolean) {
    Text(
        (if (active) "▶ " else "") + "$label ⛨ $hp",
        color = color, fontSize = 13.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun BottomControls(c: MatchController, profile: Profile, sound: SoundManager) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) { ControlsContent(c, profile, sound) }
    }
}

/** Controls body, shared by the portrait bottom bar and the wide side bar. */
@Composable
private fun ColumnScope.ControlsContent(c: MatchController, profile: Profile, sound: SoundManager) {
    Column {
        when {
            c.phase == Phase.AIMING && c.isMyTurn -> {
                AnchorBar(c)
                Spacer(Modifier.height(6.dp))
                PowerMeter(c.aimPower)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FineTune("◀", "Rotate left") { c.nudgeAngle(-it) }
                    FineTune("▶", "Rotate right") { c.nudgeAngle(it) }
                    Spacer(Modifier.weight(1f))
                    FineTune("−", "Less power") { c.nudgePower(-10 * it) }
                    FineTune("+", "More power") { c.nudgePower(10 * it) }
                }
                Spacer(Modifier.height(8.dp))
                if (c.objective.forcesRetro) {
                    // The shell is issued, not chosen: a capture is impossible
                    // without a brake, so offering the other three would be
                    // offering three ways to fail.
                    Text(
                        "Retro shell issued — burn to drop into orbit",
                        color = TextDim, fontSize = 12.sp,
                    )
                } else
                // Flow, not Row: at large system font sizes four chips no
                // longer fit one line and must wrap rather than clip.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // In a duel every shell is issued to both players, so what
                    // either of them bought cannot decide the match.
                    val available = if (c.launch.mode == GameMode.DUEL) {
                        Economy.duelLoadout()
                    } else {
                        profile.unlockedProjectiles
                    }
                    for (type in ProjectileType.entries) {
                        val unlocked = available.contains(type.id)
                        val selected = c.selectedType == type.id
                        if (selected) {
                            Button(onClick = {}, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(type.displayName, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { if (unlocked) c.selectedType = type.id },
                                enabled = unlocked,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(if (unlocked) type.displayName else "🔒 ${type.displayName}", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // heightIn, not height: fixed heights clip the label once
                // the user's font scale goes up.
                Button(
                    onClick = { c.fire() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text("FIRE", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                }
            }
            c.phase == Phase.FLYING && !c.isReplaying &&
                ProjectileType.byId(c.effectiveType).canBrake && !c.brakeUsed -> {
                Button(
                    onClick = {
                        sound.play(SoundManager.Sfx.BRAKE)
                        c.brakeNow()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DangerRed),
                ) {
                    Text("RETRO BURN", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                }
            }
        }
    }
}

/**
 * The anchor control. Anchors are the game's signature idea, so the bar is
 * always visible while aiming — it teaches by presence — and shows the budget
 * as filled pips rather than a number.
 */
@Composable
private fun AnchorBar(c: MatchController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val active = c.placingAnchors
        TextButton(
            onClick = { c.placingAnchors = !c.placingAnchors },
            modifier = Modifier.semantics {
                contentDescription = if (active) "Finish placing anchors" else "Place anchors"
            },
        ) {
            Text(
                if (active) "Done placing" else "Anchors",
                color = if (active) StarYellow else AccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        for (i in 0 until Tuning.MAX_ANCHORS) {
            val used = i < c.anchors.size
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (used) 11.dp else 9.dp)
                    .background(
                        if (used) {
                            if (c.anchors[i].attract) AccentCyan else DangerRed
                        } else PanelColor,
                        androidx.compose.foundation.shape.CircleShape,
                    )
            )
        }
        Spacer(Modifier.weight(1f))
        if (c.anchors.isNotEmpty()) {
            TextButton(onClick = { c.clearAnchors() }) {
                Text("Clear", fontSize = 13.sp, color = TextDim)
            }
        }
        // Shares this row rather than taking its own: vertical space on a
        // phone is the board, and the board is the game.
        GambitChip(c)
    }
    if (c.placingAnchors) {
        Text(
            "Tap the field to place. Tap an anchor to flip it, again to remove.",
            color = TextDim, fontSize = 11.sp,
        )
    }
}

private fun payoutLabel(g: Gambit): String = "×${g.payoutTenths / 10}.${g.payoutTenths % 10}"

/**
 * The declaration control (docs/VISION.md §3.2). Absent — not disabled — when
 * the arena has no declaration it can honour: the generator proves feasibility
 * per seat, and a control that offers an unwinnable bet is worse than none.
 */
@Composable
private fun GambitChip(c: MatchController) {
    var picking by remember { mutableStateOf(false) }
    val choices = c.gambitChoices
    if (choices.isEmpty()) return
    val g = c.selectedGambit

    TextButton(
        onClick = { picking = true },
        modifier = Modifier.semantics {
            contentDescription = if (g == Gambit.NONE) {
                "Declare a gambit"
            } else {
                "Gambit: ${g.displayName}, ${g.promise}, pays ${payoutLabel(g)}"
            }
        },
    ) {
        Text(
            if (g == Gambit.NONE) "Declare" else "${g.displayName} ${payoutLabel(g)}",
            color = if (g == Gambit.NONE) TextDim else StarYellow,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = PanelColor,
            title = { Text("Call your shot", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Keep the promise and the shot pays more. Break it and the " +
                            "shot is wasted — the hit will not count.",
                        color = TextDim, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    GambitRow(Gambit.NONE, g == Gambit.NONE) {
                        c.selectedGambit = Gambit.NONE; picking = false
                    }
                    for (option in choices) {
                        GambitRow(option, g == option) {
                            c.selectedGambit = option; picking = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text("Close", color = AccentCyan) }
            },
        )
    }
}

@Composable
private fun GambitRow(g: Gambit, selected: Boolean, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) AccentCyan.copy(alpha = 0.16f) else Color.Transparent)
            .semantics { role = Role.Button; onClick(label = "choose") { onPick(); true } }
            .pointerInput(g) { detectTapGestures { onPick() } }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                g.displayName,
                color = if (selected) AccentCyan else TextPrimary,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (g != Gambit.NONE) {
                Text(payoutLabel(g), color = StarYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(g.promise, color = TextDim, fontSize = 11.sp)
    }
}

/**
 * Power as a filled bar rather than a percentage. Players aim by feel; a
 * number like "55.0%" reads as instrumentation and tells you nothing about
 * whether the shot will reach. The bar shows fill, and the label names the
 * region ("Light", "Firm", "Full") so it can be judged at a glance.
 */
@Composable
private fun PowerMeter(power: Int) {
    val frac = power / 1000f
    val label = when {
        power < 250 -> "Light"
        power < 550 -> "Steady"
        power < 800 -> "Firm"
        power < 970 -> "Hard"
        else -> "Full"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(58.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .background(PanelColor.copy(alpha = 0.9f), RoundedCornerShape(5.dp))
                .semantics { contentDescription = "Power $label, ${power / 10} percent" }
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(AccentCyan, StarYellow, DangerRed)),
                        RoundedCornerShape(5.dp),
                    )
            )
        }
    }
}

/**
 * Fine-tune control: press and hold to repeat, rather than tapping once per
 * 0.1°. Held long enough the step itself grows (×1 → ×3 → ×8), so the same
 * control covers "nudge it a hair" and "swing it right round" without a second
 * widget. Aiming coarsely is the joystick's job; this is the vernier.
 */
@Composable
private fun FineTune(glyph: String, description: String, onStep: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(HOLD_START_MS)
        var n = 0
        while (true) {
            step(holdScale(n))
            if (n == HOLD_RAMP_1 || n == HOLD_RAMP_2) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            delay(holdInterval(n))
            n++
        }
    }

    Box(
        Modifier
            // A raw pointerInput carries no semantics, so the button's
            // TalkBack affordance is declared explicitly: screen-reader users
            // get a single step per activation, which is what they can do.
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick(label = "adjust") { step(1); true }
            }
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (pressed) AccentCyan.copy(alpha = 0.22f) else PanelColor.copy(alpha = 0.7f)
            )
            .width(58.dp)
            .heightIn(min = 48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Fire immediately so a single tap is still one step,
                        // even if it is shorter than a frame.
                        step(1)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 17.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
    }
}

/** Repeats before the step size grows. Held ≈1.0s, then ≈1.7s. */
private const val HOLD_RAMP_1 = 8
private const val HOLD_RAMP_2 = 20
/** Hold before auto-repeat begins — long enough that a tap stays a tap. */
private const val HOLD_START_MS = 320L

private fun holdScale(n: Int): Int = when {
    n < HOLD_RAMP_1 -> 1
    n < HOLD_RAMP_2 -> 3
    else -> 8
}

private fun holdInterval(n: Int): Long = when {
    n < HOLD_RAMP_1 -> 90L
    n < HOLD_RAMP_2 -> 55L
    else -> 40L
}

@Composable
private fun HandoffOverlay(
    c: MatchController,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onExit: () -> Unit,
) {
    OverlayCard {
        if (c.launch.hotseat) {
            Text(c.handoffText ?: "Next player", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("No peeking at the previous aim.", color = TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Button(onClick = { c.continueFromHandoff() }, modifier = Modifier.fillMaxWidth()) { Text("Ready — my turn") }
        } else {
            val code = c.duelCode ?: ""
            Text("Shot locked in 🔒", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Send this challenge code to your rival. Their phone will verify and replay " +
                    "your shot, then it's their turn.",
                color = TextDim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                code.take(48) + if (code.length > 48) "…" else "",
                color = AccentCyan, fontSize = 12.sp,
                modifier = Modifier
                    .background(BgColor, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onShare(ShareMessages.duelChallenge(code)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Share") }
                OutlinedButton(onClick = { onCopy(code) }, modifier = Modifier.weight(1f)) { Text("Copy") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Done — back to menu") }
        }
    }
}

@Composable
private fun SummaryOverlay(
    c: MatchController,
    s: Summary,
    profile: Profile,
    repo: ProfileRepository,
    ads: RewardedAdsProvider,
    onShare: (String) -> Unit,
    onNavigate: (Nav) -> Unit,
    exitTarget: Nav,
) {
    OverlayCard {
        Text(s.title, color = if (s.win) OkGreen else DangerRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(s.subtitle, color = TextPrimary, fontSize = 14.sp)
        if (c.launch.mode == GameMode.CAMPAIGN && s.stars > 0) {
            Spacer(Modifier.height(10.dp))
            // Stars land one at a time rather than appearing as text: this is
            // the one screen where the game says well done, and it is a
            // listed store screenshot (StarBurst).
            StarBurst(stars = s.stars)
            Spacer(Modifier.height(2.dp))
        }
        if (c.dustEarned > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "+${c.dustEarned} ✦ stardust" +
                    if (profile.adPassActive) "  (Ad Pass ×2)" else "",
                color = StarYellow, fontSize = 14.sp,
            )
        }
        // A track nobody watches filling is a track nobody plays for, so the
        // season moves in the same breath as the stardust does.
        if (c.seasonXpEarned > 0) {
            val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()
            val season = profile.rolledIntoSeason(today)
            Spacer(Modifier.height(4.dp))
            Text(
                "+${c.seasonXpEarned} season XP · tier ${season.seasonTier}",
                color = AccentCyan, fontSize = 13.sp,
            )
        }
        // Opt-in reward doubling: appears only when an ad is actually loaded,
        // never for Ad Pass holders, and never blocks the flow (docs/ADS.md).
        val adsAvailable by ads.available.collectAsState()
        val activity = LocalActivity.current
        var doubled by remember { mutableStateOf(false) }
        if (c.dustEarned > 0 && adsAvailable && !profile.adPassActive && !doubled && activity != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    ads.show(activity) {
                        val bonus = c.dustEarned
                        repo.update { p -> p.copy(stardust = p.stardust + bonus) }
                        doubled = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Double it — watch a short ad (optional)") }
        }
        Spacer(Modifier.height(16.dp))

        when (c.launch.mode) {
            GameMode.CAMPAIGN -> {
                if (s.win) {
                    val nextLevel = if (c.launch.level < Seeds.LEVELS_PER_SECTOR - 1) c.launch.level + 1 else 0
                    val nextSector = if (nextLevel == 0) c.launch.sector + 1 else c.launch.sector
                    Button(
                        onClick = {
                            onNavigate(
                                Nav.Game(
                                    GameLaunch(
                                        mode = GameMode.CAMPAIGN,
                                        seed = Seeds.campaign(nextSector, nextLevel),
                                        difficulty = Seeds.campaignDifficulty(nextSector, nextLevel),
                                        sector = nextSector,
                                        level = nextLevel,
                                        nonce = System.nanoTime(),
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next level →") }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedButton(
                    onClick = { onNavigate(Nav.Game(c.launch.copy(nonce = System.nanoTime()))) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (s.win) "Replay for more stars" else "Try again") }
            }
            GameMode.DAILY -> {
                s.shareText?.let { text ->
                    Button(onClick = { onShare(text) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Share result (with proof)")
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { onNavigate(Nav.Game(c.launch.copy(nonce = System.nanoTime()))) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Improve my score") }
            }
            GameMode.DUEL -> {
                if (c.launch.hotseat) {
                    Button(
                        onClick = {
                            onNavigate(
                                Nav.Game(
                                    c.launch.copy(
                                        seed = Seeds.duelRandom(System.nanoTime()),
                                        nonce = System.nanoTime(),
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Rematch") }
                } else {
                    s.duelCode?.let { code ->
                        Button(
                            onClick = { onShare(ShareMessages.duelResult(code)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Send final code to rival") }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { onNavigate(exitTarget) }, modifier = Modifier.fillMaxWidth()) { Text("Exit") }

        var showFeedback by remember { mutableStateOf(false) }
        TextButton(onClick = { showFeedback = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Report a problem with this match", fontSize = 12.sp, color = TextDim)
        }
        if (showFeedback) {
            FeedbackDialog(
                contextLine = "match: ${c.launch.mode} · seed ${c.spec.seed} · d${c.spec.difficulty}",
                replayCode = c.feedbackCode(),
                onDismiss = { showFeedback = false },
            )
        }
    }
}

@Composable
private fun OverlayCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = PanelColor),
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                content()
            }
        }
    }
}


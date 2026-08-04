package io.github.stan2032.gravitygambit.ui.game

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.Economy
import io.github.stan2032.gravitygambit.core.GameMode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun GameScreen(
    launch: GameLaunch,
    profile: Profile,
    repo: ProfileRepository,
    onNavigate: (Nav) -> Unit,
) {
    var controller by remember(launch) { mutableStateOf<MatchController?>(null) }
    var failed by remember(launch) { mutableStateOf(false) }

    LaunchedEffect(launch) {
        val created = withContext(Dispatchers.Default) { MatchController.create(launch) }
        if (created == null) failed = true else controller = created
    }

    val exitTarget: Nav = when (launch.mode) {
        GameMode.CAMPAIGN -> Nav.Campaign
        GameMode.DAILY -> Nav.Home
        GameMode.DUEL -> Nav.Duel
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
        else -> MatchUI(controller!!, profile, repo, exitTarget, onNavigate)
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
    exitTarget: Nav,
    onNavigate: (Nav) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showQuit by remember { mutableStateOf(false) }
    var timeSec by remember { mutableStateOf(0f) }

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
                while (isActive && c.phase == Phase.FLYING) {
                    withFrameNanos { }
                    c.advanceFrame()
                }
            }
            Phase.RESOLVING -> {
                val hit = c.lastResult?.stationHits?.isNotEmpty() == true
                if (hit && profile.hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (c.explosion != null) {
                    val start = withFrameNanos { it }
                    var p = 0f
                    while (isActive && p < 1f) {
                        p = ((withFrameNanos { it } - start) / 1e9f / 0.7f).coerceAtMost(1f)
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
        if (c.phase == Phase.OVER && !c.rewardsApplied) {
            c.rewardsApplied = true
            val s = c.summary ?: return@LaunchedEffect
            c.dustEarned = when (c.launch.mode) {
                GameMode.CAMPAIGN -> repo.recordCampaign(
                    c.launch.sector, c.launch.level, c.launch.difficulty, s.stars,
                )
                GameMode.DAILY -> repo.recordDaily(c.launch.epochDay, s.score)
                GameMode.DUEL -> {
                    val winner = c.engine.duelWinner()
                    if (!c.launch.hotseat && winner != null) {
                        repo.recordDuel(win = winner == c.localPlayer, codeDuel = true)
                    } else 0
                }
            }
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

    val previewTicks = Economy.previewTicks(profile.previewLevel)
    val previewPaths = remember(c.aimAngleDeci, c.aimPower, c.selectedType, c.hpTick, c.phase) {
        if (c.phase == Phase.AIMING && c.isMyTurn) {
            Preview.trajectories(
                c.spec.bodies, c.engine.stations, c.engine.currentShooter,
                c.aimAngleDeci, c.aimPower, c.selectedType, previewTicks,
            )
        } else emptyList()
    }

    val trailColors = remember(profile.selectedTrail, c.isReplaying) {
        val def = if (c.isReplaying) Economy.trail("classic") else Economy.trail(profile.selectedTrail)
        def.colors.map { Color(it) }
    }

    Box(Modifier.fillMaxSize().background(BgColor)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(c) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (c.phase == Phase.AIMING && c.isMyTurn) {
                                c.aiming = true
                                tr?.let { c.setAimFromDrag(it.toWorld(pos)) }
                            }
                        },
                        onDragEnd = { c.aiming = false },
                        onDragCancel = { c.aiming = false },
                        onDrag = { change, _ ->
                            if (c.phase == Phase.AIMING && c.isMyTurn) {
                                tr?.let { c.setAimFromDrag(it.toWorld(change.position)) }
                            }
                        },
                    )
                }
        ) {
            val t = tr ?: return@Canvas
            // timeSec is read here so ambient animation keeps the canvas alive.
            drawStarfield(c.spec.seed, timeSec, t)
            drawBodies(c.spec.bodies, timeSec, t)
            drawStations(c.engine.stations, c.engine.hp, c.launch.mode, timeSec, t)
            if (c.phase == Phase.AIMING && c.isMyTurn) {
                drawPreview(previewPaths, t)
                drawAim(
                    c.engine.stations[c.engine.currentShooter],
                    c.aimAngleDeci, c.aimPower,
                    if (c.engine.currentShooter == 0) AccentCyan else Color(0xFFFF8A65),
                    t,
                )
            }
            drawTrail(c.trail.toList(), trailColors, t)
            c.liveShot?.let { shot ->
                if (!shot.done) drawProjectiles(shot.projectiles(), Color.White, t)
            }
            c.explosion?.let { drawExplosion(it, c.explosionProgress, t) }
        }

        TopHud(c)
        BottomControls(c, profile)

        if (c.phase == Phase.HANDOFF) {
            HandoffOverlay(c, onShare = { text -> shareText(context, text) },
                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                onExit = { onNavigate(exitTarget) })
        }
        c.summary?.let { s ->
            if (c.phase == Phase.OVER) {
                SummaryOverlay(
                    c, s,
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
    // Read hpTick so HP text refreshes after every commit.
    val hpTick = c.hpTick
    Column(
        Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
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
        c.lastResult?.let { res ->
            if (c.phase == Phase.RESOLVING && res.styleNotes.isNotEmpty()) {
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

@Composable
private fun HpText(label: String, hp: Int, color: Color, active: Boolean) {
    Text(
        (if (active) "▶ " else "") + "$label ⛨ $hp",
        color = color, fontSize = 13.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun BottomControls(c: MatchController, profile: Profile) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when {
                c.phase == Phase.AIMING && c.isMyTurn -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "∠ ${"%.1f".format(c.aimAngleDeci / 10.0)}°   ⚡ ${"%.1f".format(c.aimPower / 10.0)}%",
                            color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(PanelColor.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        NudgeButton("−.1°") { c.nudgeAngle(-1) }
                        NudgeButton("+.1°") { c.nudgeAngle(1) }
                        NudgeButton("−1%") { c.nudgePower(-10) }
                        NudgeButton("+1%") { c.nudgePower(10) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (type in ProjectileType.entries) {
                            val unlocked = profile.unlockedProjectiles.contains(type.id)
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
                    Button(onClick = { c.fire() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("FIRE", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    }
                }
                c.phase == Phase.FLYING && !c.isReplaying &&
                    ProjectileType.byId(c.selectedType).canBrake && !c.brakeUsed -> {
                    Button(
                        onClick = { c.brakeNow() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DangerRed),
                    ) {
                        Text("RETRO BURN", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
        Text(label, fontSize = 13.sp, color = AccentCyan)
    }
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
                Button(onClick = { onShare("Your move. ⚔️ $code") }, modifier = Modifier.weight(1f)) { Text("Share") }
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
    onShare: (String) -> Unit,
    onNavigate: (Nav) -> Unit,
    exitTarget: Nav,
) {
    OverlayCard {
        Text(s.title, color = if (s.win) OkGreen else DangerRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(s.subtitle, color = TextPrimary, fontSize = 14.sp)
        if (c.launch.mode == GameMode.CAMPAIGN && s.stars > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "★".repeat(s.stars) + "☆".repeat(3 - s.stars),
                color = StarYellow, fontSize = 30.sp, letterSpacing = 6.sp,
            )
        }
        if (c.dustEarned > 0) {
            Spacer(Modifier.height(6.dp))
            Text("+${c.dustEarned} ✦ stardust", color = StarYellow, fontSize = 14.sp)
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
                            onClick = { onShare("Final round — see the result. ⚔️ $code") },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Send final code to rival") }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { onNavigate(exitTarget) }, modifier = Modifier.fillMaxWidth()) { Text("Exit") }
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

private fun shareText(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

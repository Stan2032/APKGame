package io.github.stan2032.gravitygambit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.core.ShareCodec
import io.github.stan2032.gravitygambit.ui.DangerRed
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.Nav

@Composable
fun DuelScreen(profile: Profile, onNavigate: (Nav) -> Unit) {
    ScreenFrame(title = "Duel — PVP", onBack = { onNavigate(Nav.Home) }) {

        SectionCard {
            Column {
                CardTitle("Hot-seat duel")
                Subtitle("Two players, one phone, alternating shots. First station down loses.")
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    onNavigate(
                        Nav.Game(
                            GameLaunch(
                                mode = GameMode.DUEL,
                                seed = Seeds.duelRandom(System.currentTimeMillis()),
                                difficulty = Seeds.DUEL_DIFFICULTY,
                                hotseat = true,
                            )
                        )
                    )
                }) { Text("Start hot-seat duel") }
            }
        }

        SectionCard {
            Column {
                CardTitle("Challenge a friend — no servers, no accounts")
                Subtitle(
                    "Take your shot, then send the challenge code over any messenger. " +
                        "Your friend's phone re-simulates your shot and verifies it " +
                        "mathematically — scores can't be faked. Play continues code by code."
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    onNavigate(
                        Nav.Game(
                            GameLaunch(
                                mode = GameMode.DUEL,
                                seed = Seeds.duelRandom(System.currentTimeMillis()),
                                difficulty = Seeds.DUEL_DIFFICULTY,
                                hotseat = false,
                            )
                        )
                    )
                }) { Text("Create new challenge") }
            }
        }

        SectionCard {
            Column {
                CardTitle("Got a code? Continue a duel")
                var code by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste a GG1. challenge code") },
                    minLines = 2,
                )
                if (error != null) {
                    Text(error!!, color = DangerRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = {
                        val decoded = ShareCodec.decode(code)
                        if (decoded == null || decoded.mode != GameMode.DUEL.id) {
                            error = "That doesn't look like a valid duel code."
                        } else {
                            onNavigate(
                                Nav.Game(
                                    GameLaunch(
                                        mode = GameMode.DUEL,
                                        seed = decoded.seed,
                                        difficulty = decoded.difficulty,
                                        hotseat = false,
                                        importedCode = code,
                                    )
                                )
                            )
                        }
                    }) { Text("Load duel") }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    if (profile.lastDuelCode.isNotEmpty()) {
                        OutlinedButton(onClick = { code = profile.lastDuelCode; error = null }) {
                            Text("Use my last code")
                        }
                    }
                }
            }
        }

        SectionCard {
            Column {
                CardTitle("Record")
                Subtitle("${profile.duelWins} wins · ${profile.duelLosses} losses (code duels)")
            }
        }
    }
}

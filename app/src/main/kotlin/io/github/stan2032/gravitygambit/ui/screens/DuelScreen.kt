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
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.core.ShareCodec
import io.github.stan2032.gravitygambit.ui.DangerRed
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.Nav

@Composable
fun DuelScreen(profile: Profile, onNavigate: (Nav) -> Unit) {
    ScreenFrame(title = "Duel — PVP", onBack = { onNavigate(Nav.Home) }) {

        // First-use coaching for the code exchange, mirroring the aim hint on
        // the first campaign level (GameScreen). Each card below explains its
        // own button, but nobody explains the *shape* of the thing: that a code
        // duel is a turn-by-turn exchange rather than a single sent shot. A
        // player who fires, texts a code and then receives one back has no way
        // to know that is normal, or that it goes in the box further down.
        //
        // Shown from profile state rather than a stored "seen" flag — the same
        // approach as the aim hint, and it costs no new Profile field. It
        // disappears the moment there is any evidence of a code duel: a result
        // either way, or a code of one's own.
        if (profile.duelWins == 0 && profile.duelLosses == 0 && profile.lastDuelCode.isEmpty()) {
            SectionCard {
                Column {
                    CardTitle("How a code duel works")
                    Subtitle(
                        "1. Create a challenge and take your shot.\n" +
                            "2. Text your friend the code. Their phone replays your shot exactly, " +
                            "then they fire back and send a new code.\n" +
                            "3. Paste theirs below. Each code carries the whole match so far.\n\n" +
                            "Stations take ${Tuning.STATION_HP} damage. First one down loses — or " +
                            "it's a draw if you both survive ${Tuning.DUEL_MAX_TURNS} turns. " +
                            "No account, no server, and both players get every shell.",
                    )
                }
            }
        }

        SectionCard {
            Column {
                CardTitle("Hot-seat duel")
                // "First station down loses" was true and incomplete: a duel
                // also ends at Tuning.DUEL_MAX_TURNS with both alive, and
                // MatchEngine.duelWinner() returns null there — a draw the
                // screen never mentioned. Reachable by two players who keep
                // missing, which is exactly who is playing their first duel.
                Subtitle(
                    "Two players, one phone. First station down loses; " +
                        "${Tuning.DUEL_MAX_TURNS} shots with both still standing is a draw. " +
                        "Pays no stardust or season XP — one player holding both seats is not a match."
                )
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
                CardTitle("Challenge a friend")
                Subtitle("Take your shot, then text the code to a friend. Their phone replays it.")
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
                CardTitle("Got a code?")
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

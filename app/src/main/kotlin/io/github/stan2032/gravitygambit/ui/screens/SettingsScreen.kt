package io.github.stan2032.gravitygambit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.TextPrimary

@Composable
fun SettingsScreen(profile: Profile, repo: ProfileRepository, onNavigate: (Nav) -> Unit) {
    ScreenFrame(title = "Settings", onBack = { onNavigate(Nav.Home) }) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CardTitle("Haptic feedback")
                    Subtitle("Buzz on impacts.")
                }
                Switch(
                    checked = profile.hapticsEnabled,
                    onCheckedChange = { on -> repo.update { it.copy(hapticsEnabled = on) } },
                )
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CardTitle("Sound effects")
                    Subtitle("Launches, impacts, and the occasional victory chime.")
                }
                Switch(
                    checked = profile.soundEnabled,
                    onCheckedChange = { on -> repo.update { it.copy(soundEnabled = on) } },
                )
            }
        }

        SectionCard {
            Column {
                CardTitle("Send feedback")
                Subtitle(
                    "Tell us what's broken or brilliant. Your note travels through an app " +
                        "you choose — the game itself sends nothing, ever."
                )
                Spacer(Modifier.height(8.dp))
                var showFeedback by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { showFeedback = true }) { Text("Write feedback") }
                if (showFeedback) {
                    FeedbackDialog(contextLine = null, replayCode = null, onDismiss = { showFeedback = false })
                }
            }
        }

        SectionCard {
            Column {
                CardTitle("Privacy")
                Subtitle(
                    "Gravity Gambit collects no data and has no internet permission. " +
                        "Your progress lives only on this device. Challenge codes contain " +
                        "only game moves — never personal information."
                )
            }
        }

        SectionCard {
            Column {
                CardTitle("Reset progress")
                Subtitle("Wipes stars, stardust, unlocks and records on this device. No undo.")
                Spacer(Modifier.height(8.dp))
                var confirm by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { confirm = true }) { Text("Reset everything") }
                if (confirm) {
                    AlertDialog(
                        onDismissRequest = { confirm = false },
                        title = { Text("Reset all progress?") },
                        text = { Text("This deletes your entire profile on this device. There is no undo.") },
                        confirmButton = {
                            Button(onClick = {
                                repo.update { Profile() }
                                confirm = false
                            }) { Text("Reset") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirm = false }) { Text("Keep playing") }
                        },
                    )
                }
            }
        }

        SectionCard {
            Column {
                CardTitle("About")
                Text("Gravity Gambit 0.1.0", color = TextPrimary, fontSize = 14.sp)
                Text(
                    "Deterministic N-body artillery. Same physics on every device, down to the last bit.",
                    color = TextDim, fontSize = 12.sp,
                )
            }
        }
    }
}

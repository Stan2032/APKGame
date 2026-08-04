package io.github.stan2032.gravitygambit.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.stan2032.gravitygambit.BuildConfig
import io.github.stan2032.gravitygambit.ui.shareText

/**
 * In-app feedback with an agent-facing destiny: the blob below is the
 * machine-parseable format documented in feedback/README.md. Players send it
 * through their own share sheet (the app has no network); the owner drops
 * received blobs into feedback/inbox/ in the repo, where agent sessions
 * parse, verify any attached replay code against the engine, triage into
 * TODO.md, and archive with resolution notes.
 */
object Feedback {
    const val MAGIC = "GGFB1"

    fun compose(message: String, contextLine: String?, replayCode: String?): String = buildString {
        appendLine("$MAGIC · Gravity Gambit feedback")
        appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("android: ${Build.VERSION.SDK_INT} · ${Build.MANUFACTURER} ${Build.MODEL}")
        contextLine?.let { appendLine(it) }
        replayCode?.let { appendLine("replay: $it") }
        appendLine("---")
        appendLine(message.trim())
    }
}

@Composable
fun FeedbackDialog(
    contextLine: String?,
    replayCode: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send feedback") },
        text = {
            Column {
                Subtitle(
                    "Your words, plus app version and device model" +
                        (if (replayCode != null) ", plus this match's replay code — an exact reproduction of what you just saw" else "") +
                        ". It goes out through an app you choose; the game itself sends nothing. Edit freely before sending."
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("What happened? What should be better?") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    shareText(context, Feedback.compose(text, contextLine, replayCode))
                    onDismiss()
                },
            ) { Text("Share") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

package io.github.stan2032.gravitygambit.ui

import android.content.Context
import android.content.Intent

/** Hands text to the player's own share sheet — the app itself sends nothing. */
fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

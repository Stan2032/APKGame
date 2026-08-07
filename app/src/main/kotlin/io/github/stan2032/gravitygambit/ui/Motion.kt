package io.github.stan2032.gravitygambit.ui

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Whether this device wants motion at all.
 *
 * Android lets a user set animator duration scale to zero — in Developer
 * options, and, more to the point, as the thing Accessibility guides tell
 * people with vestibular disorders or motion sensitivity to do. When they do,
 * the system stops running animators, but it cannot stop a game that draws its
 * own motion frame by frame. Ours does exactly that: the camera shake and the
 * star flourish are hand-drawn, so nothing would have honoured the setting
 * unless we asked.
 *
 * That gap existed silently from 0.13.1, when camera shake shipped. A player
 * who had switched animations off system-wide still got the board thrown
 * around on every kill, and had no in-app switch to stop it.
 *
 * What is deliberately *not* gated: anything that carries information. The
 * projectile still flies, the trail still draws, the preview still updates —
 * a reduced-motion preference means "do not decorate with movement", never
 * "hide what is happening". Only flourish is skipped, and where a flourish
 * would have revealed something (the stars), the end state is drawn at once
 * instead of not at all.
 */
@Composable
fun animationsEnabled(): Boolean = remember {
    // Cheap and stable enough to read once per composition scope: the setting
    // requires leaving the app to change, so re-reading per frame would burn
    // a binder call for a value that cannot move mid-match.
    runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
}

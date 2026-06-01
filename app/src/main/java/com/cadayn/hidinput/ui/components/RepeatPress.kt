package com.cadayn.hidinput.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fire-on-press with a real keyboard-style auto-repeat: one event on press, then — if [enabled] —
 * a hold begins repeating after an initial delay and accelerates (350ms → 110ms → down to 28ms),
 * stopping on release. A quick tap fires exactly once. Used by both the keyboard and the remote.
 */
fun Modifier.repeatingPress(enabled: Boolean, haptic: Boolean, view: View, onFire: () -> Unit): Modifier =
    pointerInput(enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun fire() {
                if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onFire()
            }
            fire()
            if (enabled) {
                var wait = 350L
                var first = true
                while (true) {
                    val released = withTimeoutOrNull(wait) {
                        do {
                            val e = awaitPointerEvent()
                        } while (e.changes.any { it.id == down.id && it.pressed })
                        true
                    }
                    if (released == true) break
                    fire()
                    wait = if (first) { first = false; 110L } else maxOf(28L, wait - 12L)
                }
            } else {
                waitForUpOrCancellation()
            }
        }
    }

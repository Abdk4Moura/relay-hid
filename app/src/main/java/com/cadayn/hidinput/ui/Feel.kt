package com.cadayn.hidinput.ui

import kotlin.math.exp
import kotlin.math.ln

/**
 * The single "Feel" knob: one perceptual axis (calmer <-> snappier, 9 detents 0..8) that moves
 * several coupled trackpad parameters together along a curated 1-D path. This is the proven
 * libinput / Windows pointer-speed / game-sensitivity pattern: one control reshapes a whole curve,
 * so the user never sees gMin or decay.
 *
 * The path interpolates between three hand-authored anchor vectors (calm / neutral / snappy).
 * Gains interpolate in LOG space (perceived multiplicatively, Weber), thresholds linearly, and the
 * momentum retention via its time constant (it sits near 1.0 where linear lerp distorts).
 *
 * Design + anchors come from docs/comfort-calibration-research.md.
 */
data class FeelProfile(
    val gMin: Float,        // pointer precision floor (gain at the slowest finger speed)
    val gMaxMul: Float,     // multiplier on the accel-derived ceiling
    val sMin: Float,        // slow threshold (dp/ms): where accel starts ramping
    val sMax: Float,        // fast threshold (dp/ms): where accel hits the ceiling
    val scrollMul: Float,   // scroll-gain character (relative to the neutral 0.8 baseline)
    val momDecay: Float,    // momentum retention PER MILLISECOND (engine decays momVel *= d^dtMs)
    val flickBoost: Float,  // fling launch multiplier
)

object Feel {
    const val DETENTS = 9          // indices 0..8
    const val NEUTRAL = 4

    // per-16ms anchors from the research, converted to per-ms here: d_ms = d16^(1/16)
    private fun perMs(d16: Float): Float = Math.pow(d16.toDouble(), 1.0 / 16.0).toFloat()

    private val CALM = FeelProfile(
        gMin = 0.30f, gMaxMul = 0.85f, sMin = 0.30f, sMax = 3.0f,
        scrollMul = 0.6f, momDecay = perMs(0.90f), flickBoost = 1.15f,
    )
    private val NEUT = FeelProfile(
        gMin = 0.40f, gMaxMul = 1.00f, sMin = 0.20f, sMax = 2.6f,
        scrollMul = 0.8f, momDecay = perMs(0.94f), flickBoost = 1.4f,
    )
    private val SNAP = FeelProfile(
        gMin = 0.55f, gMaxMul = 1.20f, sMin = 0.12f, sMax = 2.1f,
        scrollMul = 1.3f, momDecay = perMs(0.965f), flickBoost = 1.8f,
    )

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun logLerp(a: Float, b: Float, t: Float) = exp(lerp(ln(a), ln(b), t))
    // interpolate a near-1 retention fraction through its time constant (tau = -1/ln d)
    private fun decayLerp(a: Float, b: Float, t: Float): Float {
        val ta = -1f / ln(a); val tb = -1f / ln(b)
        return exp(-1f / lerp(ta, tb, t))
    }

    private fun blend(a: FeelProfile, b: FeelProfile, t: Float) = FeelProfile(
        gMin = logLerp(a.gMin, b.gMin, t),
        gMaxMul = logLerp(a.gMaxMul, b.gMaxMul, t),
        sMin = lerp(a.sMin, b.sMin, t),
        sMax = lerp(a.sMax, b.sMax, t),
        scrollMul = logLerp(a.scrollMul, b.scrollMul, t),
        momDecay = decayLerp(a.momDecay, b.momDecay, t),
        flickBoost = logLerp(a.flickBoost, b.flickBoost, t),
    )

    /** Precomputed 9-row table: index by feel 0..8. Calm at 0, neutral at 4, snappy at 8. */
    private val TABLE: Array<FeelProfile> = Array(DETENTS) { f ->
        val u = f / (DETENTS - 1).toFloat()        // 0..1
        if (u <= 0.5f) blend(CALM, NEUT, u / 0.5f)
        else blend(NEUT, SNAP, (u - 0.5f) / 0.5f)
    }

    fun profile(feel: Int): FeelProfile = TABLE[feel.coerceIn(0, DETENTS - 1)]

    val labels = arrayOf("Calm", "Gentle", "Soft", "Easy", "Balanced", "Lively", "Quick", "Brisk", "Snappy")
    fun label(feel: Int): String = labels[feel.coerceIn(0, DETENTS - 1)]
}

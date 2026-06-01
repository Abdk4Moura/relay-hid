package com.cadayn.hidinput.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cadayn.hidinput.ui.theme.Relay

/**
 * Minimal stroke icons drawn on a 24-unit virtual canvas, matching the prototype's
 * geometric line-icon style. Avoids pulling in material-icons-extended.
 */
object RelayIcons {
    @Composable fun Grid(size: Dp = 18.dp, color: Color = Relay.colors.textDim) =
        IconCanvas(size, color) { s, w ->
            val r = w * 0.06f
            listOf(3f to 3f, 13.5f to 3f, 3f to 13.5f, 13.5f to 13.5f).forEach { (x, y) ->
                drawRoundRect(color, Offset(s(x), s(y)), androidx.compose.ui.geometry.Size(s(7.5f), s(7.5f)),
                    androidx.compose.ui.geometry.CornerRadius(r, r), style = stroke(w))
            }
        }

    @Composable fun Keyboard(size: Dp = 20.dp, color: Color = Relay.colors.textDim) =
        IconCanvas(size, color) { s, w ->
            drawRoundRect(color, Offset(s(2.5f), s(6f)), androidx.compose.ui.geometry.Size(s(19f), s(12f)),
                androidx.compose.ui.geometry.CornerRadius(s(2.5f), s(2.5f)), style = stroke(w))
            listOf(7f, 11f, 15f).forEach { drawLine(color, Offset(s(it), s(10f)), Offset(s(it + 0.1f), s(10f)), w * 1.4f, StrokeCap.Round) }
            drawLine(color, Offset(s(8.5f), s(13.5f)), Offset(s(15.5f), s(13.5f)), w, StrokeCap.Round)
        }

    @Composable fun Sliders(size: Dp = 18.dp, color: Color = Relay.colors.textDim) =
        IconCanvas(size, color) { s, w ->
            drawLine(color, Offset(s(4f), s(7f)), Offset(s(14f), s(7f)), w, StrokeCap.Round)
            drawLine(color, Offset(s(18f), s(7f)), Offset(s(20f), s(7f)), w, StrokeCap.Round)
            drawLine(color, Offset(s(4f), s(17f)), Offset(s(6f), s(17f)), w, StrokeCap.Round)
            drawLine(color, Offset(s(10f), s(17f)), Offset(s(20f), s(17f)), w, StrokeCap.Round)
            drawCircle(color, s(2.2f), Offset(s(16f), s(7f)), style = stroke(w))
            drawCircle(color, s(2.2f), Offset(s(8f), s(17f)), style = stroke(w))
        }

    @Composable fun Gear(size: Dp = 18.dp, color: Color = Relay.colors.textDim) =
        IconCanvas(size, color) { s, w ->
            drawCircle(color, s(3.2f), Offset(s(12f), s(12f)), style = stroke(w))
            val spokes = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
            spokes.forEach { deg ->
                val a = Math.toRadians(deg.toDouble())
                val cx = s(12f); val cy = s(12f)
                val r1 = s(6.2f); val r2 = s(8.2f)
                drawLine(color,
                    Offset(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat()),
                    Offset(cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat()),
                    w, StrokeCap.Round)
            }
        }

    @Composable fun Bluetooth(size: Dp = 18.dp, color: Color = Relay.colors.text) =
        IconCanvas(size, color) { s, w ->
            val p = Path().apply {
                moveTo(s(7f), s(7.5f)); lineTo(s(17f), s(16f)); lineTo(s(12f), s(20f))
                lineTo(s(12f), s(4f)); lineTo(s(17f), s(8f)); lineTo(s(7f), s(16.5f))
            }
            drawPath(p, color, style = stroke(w, true))
        }

    @Composable fun ChevronRight(size: Dp = 16.dp, color: Color = Relay.colors.textFaint) =
        IconCanvas(size, color) { s, w ->
            val p = Path().apply { moveTo(s(9f), s(5f)); lineTo(s(16f), s(12f)); lineTo(s(9f), s(19f)) }
            drawPath(p, color, style = stroke(w * 1.1f, true))
        }

    @Composable fun Check(size: Dp = 16.dp, color: Color = Relay.colors.accent) =
        IconCanvas(size, color) { s, w ->
            val p = Path().apply { moveTo(s(4f), s(12f)); lineTo(s(9f), s(17f)); lineTo(s(20f), s(6f)) }
            drawPath(p, color, style = stroke(w * 1.2f, true))
        }

    @Composable fun Wifi(size: Dp = 18.dp, color: Color = Relay.colors.text) =
        IconCanvas(size, color) { s, w ->
            val cx = s(12f); val cy = s(18f)
            // three nested arcs + a dot, fanning up from the base point
            listOf(4.5f, 7.5f, 10.5f).forEach { r ->
                drawArc(color, 225f, 90f, false,
                    topLeft = Offset(cx - s(r), cy - s(r)),
                    size = androidx.compose.ui.geometry.Size(s(r) * 2, s(r) * 2), style = stroke(w, true))
            }
            drawCircle(color, w * 0.7f, Offset(cx, cy - s(0.5f)))
        }

    @Composable fun Monitor(size: Dp = 18.dp, color: Color = Relay.colors.text) =
        IconCanvas(size, color) { s, w ->
            drawRoundRect(color, Offset(s(3f), s(4f)), androidx.compose.ui.geometry.Size(s(18f), s(12f)),
                androidx.compose.ui.geometry.CornerRadius(s(2f), s(2f)), style = stroke(w))
            drawLine(color, Offset(s(8.5f), s(20f)), Offset(s(15.5f), s(20f)), w, StrokeCap.Round)
            drawLine(color, Offset(s(12f), s(16f)), Offset(s(12f), s(20f)), w, StrokeCap.Round)
        }

    @Composable fun Plus(size: Dp = 16.dp, color: Color = Relay.colors.text) =
        IconCanvas(size, color) { s, w ->
            drawLine(color, Offset(s(12f), s(5f)), Offset(s(12f), s(19f)), w, StrokeCap.Round)
            drawLine(color, Offset(s(5f), s(12f)), Offset(s(19f), s(12f)), w, StrokeCap.Round)
        }
}

private fun DrawScope.stroke(w: Float, round: Boolean = false) =
    Stroke(width = w, cap = if (round) StrokeCap.Round else StrokeCap.Butt, join = StrokeJoin.Round)

@Composable
private fun IconCanvas(size: Dp, color: Color, draw: DrawScope.(s: (Float) -> Float, w: Float) -> Unit) {
    Canvas(Modifier.size(size)) {
        val dim = this.size.minDimension
        val s = { v: Float -> v / 24f * dim }
        val w = dim * 0.072f
        draw(s, w)
    }
}

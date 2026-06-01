package com.cadayn.hidinput.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.theme.Relay
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class Tool { PEN, ARROW, BOX, CIRCLE }
private data class DrawnShape(val tool: Tool, val points: List<Offset>)

/**
 * Full-screen annotation layer for precise feedback: draw pencil/arrow/box/circle marks
 * over the live app, then Capture saves the annotated screen to the gallery to share.
 */
@Composable
fun DebugOverlay(onClose: () -> Unit) {
    val col = Relay.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    var tool by remember { mutableStateOf(Tool.PEN) }
    var shapes by remember { mutableStateOf(listOf<DrawnShape>()) }
    var current by remember { mutableStateOf<DrawnShape?>(null) }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(capturing) {
        if (capturing) {
            delay(80) // let the toolbar hide before grabbing the frame
            captureWindow(context) { ok ->
                Toast.makeText(context, if (ok) "Saved to Pictures/Relay" else "Capture failed", Toast.LENGTH_SHORT).show()
                capturing = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(tool) {
                detectDragGestures(
                    onDragStart = { o -> current = DrawnShape(tool, listOf(o, o)) },
                    onDrag = { change, _ ->
                        change.consume()
                        val p = change.position
                        current = current?.let { s ->
                            if (s.tool == Tool.PEN) s.copy(points = s.points + p) else s.copy(points = listOf(s.points.first(), p))
                        }
                    },
                    onDragEnd = { current?.let { shapes = shapes + it }; current = null },
                    onDragCancel = { current = null },
                )
            },
        ) {
            val w = 4.dp.toPx()
            (shapes + listOfNotNull(current)).forEach { drawAnno(it, col.accent, w) }
        }

        if (!capturing) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(13.dp)).background(col.surface)
                    .border(1.dp, col.borderHi, RoundedCornerShape(13.dp)).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip("pen", tool == Tool.PEN) { tool = Tool.PEN }
                Chip("arw", tool == Tool.ARROW) { tool = Tool.ARROW }
                Chip("box", tool == Tool.BOX) { tool = Tool.BOX }
                Chip("circ", tool == Tool.CIRCLE) { tool = Tool.CIRCLE }
                Chip("undo", false) { if (shapes.isNotEmpty()) shapes = shapes.dropLast(1) }
                Chip("clr", false) { shapes = emptyList() }
                Chip("save", false, accent = true) { capturing = true }
                Chip("✕", false) { onClose() }
            }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    val col = Relay.colors
    val fg = when { active -> col.accent; accent -> col.accent2; else -> col.textDim }
    Box(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (active) col.accentGhost else col.surface2)
            .border(1.dp, if (active) col.accentDim else col.border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp),
    ) { Text(label, style = Relay.type.mono.copy(color = fg, fontSize = 11.sp)) }
}

@Composable
private fun Sep() {
    Box(Modifier.width(1.dp).size(1.dp, 22.dp).background(Relay.colors.border))
}

private fun DrawScope.drawAnno(s: DrawnShape, color: Color, w: Float) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (s.tool) {
        Tool.PEN -> {
            if (s.points.size < 2) return
            val path = Path().apply {
                moveTo(s.points[0].x, s.points[0].y)
                s.points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color, style = stroke)
        }
        Tool.ARROW -> {
            val a = s.points.first(); val b = s.points.last()
            drawLine(color, a, b, w, StrokeCap.Round)
            val ang = atan2(b.y - a.y, b.x - a.x); val len = 34f
            drawLine(color, b, Offset(b.x - len * cos(ang - 0.5f), b.y - len * sin(ang - 0.5f)), w, StrokeCap.Round)
            drawLine(color, b, Offset(b.x - len * cos(ang + 0.5f), b.y - len * sin(ang + 0.5f)), w, StrokeCap.Round)
        }
        Tool.BOX -> {
            val a = s.points.first(); val b = s.points.last()
            drawRect(color, Offset(min(a.x, b.x), min(a.y, b.y)), Size(abs(b.x - a.x), abs(b.y - a.y)), style = stroke)
        }
        Tool.CIRCLE -> {
            val a = s.points.first(); val b = s.points.last()
            drawOval(color, Offset(min(a.x, b.x), min(a.y, b.y)), Size(abs(b.x - a.x), abs(b.y - a.y)), style = stroke)
        }
    }
}

private fun captureWindow(context: Context, onDone: (Boolean) -> Unit) {
    val activity = context.findActivity() ?: return onDone(false)
    val window = activity.window
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) return onDone(false)
    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(window, bmp, { res ->
            val ok = res == PixelCopy.SUCCESS && saveToGallery(context, bmp)
            onDone(ok)
        }, Handler(Looper.getMainLooper()))
    } catch (e: Exception) {
        onDone(false)
    }
}

private fun saveToGallery(context: Context, bmp: Bitmap): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "relay-debug-${System.currentTimeMillis()}.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Relay")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os) } } != null
} catch (e: Exception) {
    false
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

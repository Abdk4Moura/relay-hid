package com.cadayn.hidinput.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.theme.Relay

/** The Relay gradient logo mark (rounded square with a cut-out). */
@Composable
fun RelayLogo(size: Int = 22, corner: Int = 7) {
    val c = Relay.colors
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(Brush.linearGradient(listOf(c.accent, c.accent2))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((size * 0.36f).dp)
                .clip(RoundedCornerShape((corner * 0.3f).dp))
                .background(c.bgDeep),
        )
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Relay.type.eyebrow.copy(color = Relay.colors.textFaint), modifier = modifier)
}

enum class BtnKind { Primary, Secondary, Ghost }

@Composable
fun RelayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Secondary,
    large: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Relay.colors
    val shape = RoundedCornerShape(11.dp)
    val bgMod = when (kind) {
        BtnKind.Primary -> Modifier.background(Brush.verticalGradient(listOf(c.accent, c.accent2)), shape)
        BtnKind.Secondary -> Modifier.background(c.surface2, shape).border(1.dp, c.border, shape)
        BtnKind.Ghost -> Modifier
    }
    val fg = when (kind) {
        BtnKind.Primary -> c.bgDeep
        BtnKind.Ghost -> c.textDim
        else -> c.text
    }
    Row(
        modifier
            .clip(shape)
            .then(bgMod)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = if (large) 46.dp else 40.dp)
            .padding(horizontal = if (large) 22.dp else 16.dp, vertical = if (large) 12.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text, style = Relay.type.label.copy(color = fg, fontSize = if (large) 14.5.sp else 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
fun RelayCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Relay.colors.border,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(Relay.colors.surface, shape)
            .border(BorderStroke(1.dp, borderColor), shape),
    ) { content() }
}

/** A small text helper bound to the palette. */
@Composable
fun TText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Relay.colors.text,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier = modifier, style = style.copy(color = color), textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

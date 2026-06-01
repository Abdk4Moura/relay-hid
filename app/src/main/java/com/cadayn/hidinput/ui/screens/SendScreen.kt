package com.cadayn.hidinput.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.RelaySwitch
import com.cadayn.hidinput.ui.components.SectionTitle
import com.cadayn.hidinput.ui.components.SettingRow
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.Relay

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SendScreen(c: RelayController) {
    val col = Relay.colors
    var text by remember { mutableStateOf("") }
    var addReturn by remember { mutableStateOf(false) }

    fun send(s: String) {
        if (s.isEmpty()) return
        c.typeText(if (addReturn) s + "\n" else s)
    }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 24.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TText("Type & Send", Relay.type.h1, col.text)
            TText("Type or dictate here on your phone, then fire it to the host as keystrokes — perfect for logins, Wi-Fi keys, URLs and TV search boxes.",
                Relay.type.sub, col.textDim)

            // editor — a clearly-defined input card (was near-black, read as an empty void)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(col.surface)
                    .border(1.dp, col.border, RoundedCornerShape(14.dp)).padding(14.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = col.text, fontSize = 17.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(col.accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp, max = 220.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Type here… (tap the mic on your keyboard to dictate)",
                            style = TextStyle(color = col.textFaint, fontSize = 17.sp))
                        inner()
                    },
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RelayButton("Send", { send(text) }, Modifier.weight(1f), kind = BtnKind.Primary, large = true)
                RelayButton("Paste", { text = c.clipboardText() }, kind = BtnKind.Secondary)
                RelayButton("Clear", { text = "" }, kind = BtnKind.Ghost)
            }

            SettingRow("Press Enter after sending", "Submits search boxes / login fields automatically") {
                RelaySwitch(addReturn) { addReturn = it }
            }
            RelayButton("Save as snippet", { c.addSnippet(text) }, kind = BtnKind.Secondary)

            if (c.snippets.isNotEmpty()) {
                SectionTitle("Snippets")
                TText("Tap to send · long-press to delete", Relay.type.sub.copy(fontSize = 11.5.sp), col.textFaint)
                Spacer(Modifier.padding(top = 2.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.snippets.forEach { snip -> SnippetChip(snip, onSend = { send(snip) }, onDelete = { c.removeSnippet(snip) }) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SnippetChip(text: String, onSend: () -> Unit, onDelete: () -> Unit) {
    val col = Relay.colors
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(col.surface)
            .border(1.dp, col.border, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onSend, onLongClick = onDelete)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, style = Relay.type.mono.copy(color = col.textDim, fontSize = 12.sp), maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

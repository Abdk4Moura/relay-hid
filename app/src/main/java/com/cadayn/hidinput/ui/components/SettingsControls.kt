package com.cadayn.hidinput.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadayn.hidinput.ui.theme.Relay
import kotlin.math.roundToInt

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = Relay.type.eyebrow.copy(color = Relay.colors.textFaint),
        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingRow(label: String, desc: String? = null, control: @Composable () -> Unit) {
    val col = Relay.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Relay.type.label.copy(color = col.text))
            if (desc != null) {
                Spacer(Modifier.size(2.dp))
                Text(desc, style = Relay.type.sub.copy(color = col.textDim, fontSize = 11.5.sp))
            }
        }
        control()
    }
    Box(Modifier.fillMaxWidth().size(1.dp).background(col.border))
}

@Composable
fun RelaySwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val col = Relay.colors
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier.size(44.dp, 26.dp).clip(shape)
            .background(if (on) col.accent else col.surfaceHi)
            .border(1.dp, if (on) Color.Transparent else col.border, shape)
            .clickable { onChange(!on) },
    ) {
        Box(
            Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 3.dp).size(19.dp).clip(CircleShape)
                .background(if (on) col.bgDeep else col.text),
        )
    }
}

@Composable
fun RelaySlider(value: Int, min: Int, max: Int, unit: String = "", onChange: (Int) -> Unit) {
    val col = Relay.colors
    Row(Modifier.width(206.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = col.accent,
                activeTrackColor = col.accent,
                inactiveTrackColor = col.borderHi,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        Text("$value$unit", style = Relay.type.mono.copy(color = col.accent2, fontSize = 12.sp), modifier = Modifier.width(34.dp))
    }
}

@Composable
fun Seg(value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    val col = Relay.colors
    Row(
        Modifier.clip(RoundedCornerShape(11.dp)).background(col.bgDeep)
            .border(1.dp, col.border, RoundedCornerShape(11.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (v, lbl) ->
            val on = v == value
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (on) col.surfaceHi else Color.Transparent)
                    .clickable { onChange(v) }.padding(horizontal = 13.dp, vertical = 7.dp),
            ) { Text(lbl, style = Relay.type.label.copy(color = if (on) col.text else col.textDim, fontSize = 12.5.sp)) }
        }
    }
}

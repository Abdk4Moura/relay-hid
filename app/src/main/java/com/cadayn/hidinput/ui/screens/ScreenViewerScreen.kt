package com.cadayn.hidinput.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.viewinterop.AndroidView
import com.cadayn.hidinput.ui.RelayController
import com.cadayn.hidinput.ui.components.BtnKind
import com.cadayn.hidinput.ui.components.RelayButton
import com.cadayn.hidinput.ui.components.TText
import com.cadayn.hidinput.ui.theme.Relay

/**
 * Live screen of the controlled machine, in a WebView pointed at the desktop receiver's MJPEG
 * stream (served by agent/stream.py). No decode code on our side: the browser renders the
 * multipart stream natively. Only reachable when the target advertises the screen-stream capability
 * (a desktop on WiFi). If the stream is not running on the host, the page shows its own hint.
 */
@Composable
fun ScreenViewerScreen(c: RelayController, onBack: () -> Unit) {
    val col = Relay.colors
    val url = c.screenStreamUrl()
    Column(Modifier.fillMaxSize().background(col.bg)) {
        Row(
            Modifier.fillMaxWidth().background(col.surface).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TText("Screen", Relay.type.h2, col.text)
            Text(c.activeName ?: "host", style = Relay.type.mono.copy(color = col.textFaint, fontSize = 11.sp))
            Spacer(Modifier.weight(1f))
            RelayButton("Close", onBack, kind = BtnKind.Ghost)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (url == null) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Live screen needs a WiFi desktop connection.",
                        style = Relay.type.body.copy(color = col.textDim, fontSize = 15.sp))
                    Spacer(Modifier.padding(6.dp))
                    Text("Connect to a computer over WiFi, then start the screen stream on it.",
                        style = Relay.type.mono.copy(color = col.textFaint, fontSize = 12.sp))
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true   // viewer page reads the token + sets the stream <img> via JS
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            setBackgroundColor(android.graphics.Color.BLACK)
                            loadUrl(url)
                        }
                    },
                )
            }
        }
    }
}

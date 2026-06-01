package com.cadayn.hidinput

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.cadayn.hidinput.ui.RelayController

/**
 * Invisible share target: "Share → Relay" from any app sends text (→ desktop clipboard)
 * or files (→ desktop Downloads) over the active WiFi link, then returns you to the app
 * you came from. Reuses the singleton controller so it rides the existing connection.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = RelayController.getInstance(applicationContext)
        if (!c.wifiActive) {
            Toast.makeText(this, "Relay: connect to a desktop first", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrEmpty()) {
                        c.shareText(text)
                        Toast.makeText(this, "Sent to desktop", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) { sendFile(c, uri); Toast.makeText(this, "Sending file…", Toast.LENGTH_SHORT).show() }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION") val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.forEach { sendFile(c, it) }
                Toast.makeText(this, "Sending ${uris?.size ?: 0} files…", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun sendFile(c: RelayController, uri: Uri) {
        val cr = contentResolver
        Thread {
            val bytes = runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@Thread
            var name = "file"
            runCatching {
                cr.query(uri, null, null, null, null)?.use { cu ->
                    val i = cu.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && cu.moveToFirst()) cu.getString(i)?.let { name = it }
                }
            }
            c.wifiSendFile(name, bytes)
        }.start()
    }
}

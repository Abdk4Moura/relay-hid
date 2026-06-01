package com.cadayn.hidinput

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * WiFi transport to a Relay desktop receiver. Sends newline-delimited JSON events
 * over TCP (same logical events as the HID path). All socket work runs on a single
 * worker thread so ordering is preserved and the UI never blocks.
 */
class WifiLink {

    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var socket: Socket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile var connected = false; private set
    @Volatile var host: String? = null; private set

    /** Called (off the main thread) with text the desktop pushed down (clipboard sync). */
    var onClip: ((String) -> Unit)? = null

    fun connect(ip: String, port: Int, token: String, onResult: (Boolean) -> Unit) {
        worker.execute {
            closeQuietly()
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 3000)
                s.tcpNoDelay = true
                out = s.getOutputStream()
                socket = s
                host = ip
                writeLine("""{"t":"hello","token":"${esc(token)}"}""")
                connected = true
                startReader(s)
                onResult(true)
            } catch (e: Exception) {
                connected = false
                closeQuietly()
                onResult(false)
            }
        }
    }

    /** Reverse channel: read newline-JSON the desktop sends (currently clipboard). */
    private fun startReader(s: Socket) {
        Thread {
            runCatching {
                val br = s.getInputStream().bufferedReader()
                while (connected) {
                    val line = br.readLine() ?: break
                    if (line.isBlank()) continue
                    runCatching {
                        val o = org.json.JSONObject(line)
                        if (o.optString("t") == "clip") onClip?.invoke(o.optString("s"))
                    }
                }
            }
            connected = false
        }.apply { isDaemon = true }.start()
    }

    fun clip(s: String) = send("""{"t":"clip","s":"${esc(s)}"}""")
    fun file(name: String, b64: String) = send("""{"t":"file","name":"${esc(name)}","data":"$b64"}""")

    fun disconnect() {
        worker.execute { closeQuietly(); connected = false; host = null }
    }

    // ---- event senders (cheap string building; one line each) ----
    fun key(mods: Int, code: Int) = send("""{"t":"key","code":$code,"mods":$mods}""")
    fun move(dx: Int, dy: Int) = send("""{"t":"move","dx":$dx,"dy":$dy}""")
    fun scroll(dy: Int) = send("""{"t":"scroll","dy":$dy}""")
    fun button(b: String, down: Boolean) = send("""{"t":"button","b":"$b","down":$down}""")
    fun consumer(usage: Int) = send("""{"t":"consumer","usage":$usage}""")
    fun text(s: String) = send("""{"t":"text","s":"${esc(s)}"}""")

    private fun send(line: String) {
        if (!connected) return
        worker.execute { writeLine(line) }
    }

    private fun writeLine(line: String) {
        try {
            out?.write((line + "\n").toByteArray(Charsets.UTF_8))
            out?.flush()
        } catch (e: Exception) {
            connected = false
            closeQuietly()
        }
    }

    private fun closeQuietly() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        out = null
    }

    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append(' ') else append(c)
        }
    }
}

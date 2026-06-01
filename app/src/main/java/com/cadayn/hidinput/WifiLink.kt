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
    @Volatile private var intentional = false

    /** Called (off the main thread) with text the desktop pushed down (clipboard sync). */
    var onClip: ((String) -> Unit)? = null
    /** Called (off the main thread) when the desktop acks a file (ok, name) — ok=false on failure. */
    var onFileAck: ((Boolean, String) -> Unit)? = null
    /** Desktop→phone transfer: progress (name, 0..100) and completion (name, tempFile). */
    var onFileProgress: ((String, Int) -> Unit)? = null
    var onFileReceived: ((String, java.io.File) -> Unit)? = null
    /** Scratch dir for streaming incoming files to disk (set by the controller to the app cache). */
    var cacheDir: java.io.File? = null
    private class Rx(val name: String, val size: Long, val file: java.io.File, val out: java.io.OutputStream) { var got = 0L }
    private val incoming = HashMap<Long, Rx>()
    /** Called (off the main thread) when the link drops *unexpectedly* (not a user disconnect). */
    var onDisconnect: (() -> Unit)? = null

    fun connect(ip: String, port: Int, token: String, onResult: (Boolean) -> Unit) {
        worker.execute {
            closeQuietly()
            intentional = false
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 3000)
                s.tcpNoDelay = true
                s.keepAlive = true
                val o = s.getOutputStream()
                o.write(("""{"t":"hello","token":"${esc(token)}"}""" + "\n").toByteArray(Charsets.UTF_8))
                o.flush()
                // Wait briefly for the desktop's auth confirmation. A closed socket (EOF) means the
                // PIN was rejected → fail. A line (welcome/data) means accepted. A timeout means an
                // older receiver that doesn't ack — assume accepted (the link is still open).
                s.soTimeout = 4000
                val br = s.getInputStream().bufferedReader()
                val first = try { br.readLine() } catch (_: java.net.SocketTimeoutException) { "" }
                if (first == null) throw java.io.IOException("rejected") // EOF → bad PIN / dropped
                s.soTimeout = 0
                out = o
                socket = s
                host = ip
                connected = true
                startReader(s, br, first)
                startHeartbeat()
                onResult(true)
            } catch (e: Exception) {
                connected = false
                closeQuietly()
                onResult(false)
            }
        }
    }

    /** App-level heartbeat so the desktop can tell a live-but-idle phone from a dead socket. */
    private fun startHeartbeat() {
        Thread {
            while (connected) {
                try { Thread.sleep(4000) } catch (_: InterruptedException) { break }
                if (connected) send("""{"t":"ping"}""")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return
        runCatching {
            val o = org.json.JSONObject(line)
            when (o.optString("t")) {
                "clip" -> onClip?.invoke(o.optString("s"))
                "fileok" -> onFileAck?.invoke(true, o.optString("name"))
                "fileerr" -> onFileAck?.invoke(false, o.optString("name"))
                "fstart" -> cacheDir?.let { dir ->
                    runCatching {
                        val tmp = java.io.File.createTempFile("relay-rx", ".part", dir)
                        incoming[o.optLong("id")] = Rx(o.optString("name"), o.optLong("size"), tmp, java.io.BufferedOutputStream(java.io.FileOutputStream(tmp)))
                    }
                }
                "fchunk" -> incoming[o.optLong("id")]?.let { rx ->
                    val b = android.util.Base64.decode(o.optString("data"), android.util.Base64.NO_WRAP)
                    rx.out.write(b); rx.got += b.size
                    val pct = if (rx.size > 0) (rx.got * 100 / rx.size).toInt().coerceAtMost(100) else 0
                    onFileProgress?.invoke(rx.name, pct)
                }
                "fend" -> incoming.remove(o.optLong("id"))?.let { rx ->
                    runCatching { rx.out.flush(); rx.out.close() }
                    onFileReceived?.invoke(rx.name, rx.file)
                }
                else -> {}   // "welcome" and unknowns: ignore
            }
        }
    }

    /** Reverse channel: read newline-JSON the desktop sends. `first` is the line already consumed
     *  during the auth handshake (may be "welcome", real data, or empty on timeout). */
    private fun startReader(s: Socket, br: java.io.BufferedReader, first: String?) {
        Thread {
            runCatching {
                if (!first.isNullOrEmpty()) handleLine(first)
                while (connected) {
                    val line = br.readLine() ?: break
                    handleLine(line)
                }
            }
            val wasConnected = connected
            connected = false
            if (wasConnected && !intentional) onDisconnect?.invoke()
        }.apply { isDaemon = true }.start()
    }

    fun clip(s: String) = send("""{"t":"clip","s":"${esc(s)}"}""")
    fun file(name: String, b64: String) = send("""{"t":"file","name":"${esc(name)}","data":"$b64"}""")

    @Volatile private var fileSeq = 0L
    /** Stream a file from an InputStream in 128KB chunks — constant memory, so any size works. */
    fun sendFileStream(name: String, size: Long, input: java.io.InputStream, onProgress: (Int) -> Unit, onDone: (Boolean) -> Unit) {
        worker.execute {
            if (!connected) { runCatching { input.close() }; onDone(false); return@execute }
            val id = ++fileSeq
            writeLine("""{"t":"fstart","id":$id,"name":"${esc(name)}","size":$size}""")
            val buf = ByteArray(128 * 1024); var sent = 0L
            try {
                input.use { ins ->
                    while (connected) {
                        val n = ins.read(buf); if (n < 0) break
                        writeLine("""{"t":"fchunk","id":$id,"data":"${android.util.Base64.encodeToString(buf, 0, n, android.util.Base64.NO_WRAP)}"}""")
                        sent += n
                        onProgress(if (size > 0) (sent * 100 / size).toInt().coerceAtMost(100) else 0)
                    }
                }
            } catch (e: Exception) { connected = false; closeQuietly() }
            if (connected) writeLine("""{"t":"fend","id":$id}""")
            onDone(connected)
        }
    }
    /** Stream a file in chunks (fstart/fchunk/fend) so the desktop can show progress and large
     *  files don't blow memory in one base64 blob. onProgress is 0..100; onDone(false) = link lost. */
    fun sendFileChunked(name: String, bytes: ByteArray, onProgress: (Int) -> Unit, onDone: (Boolean) -> Unit) {
        worker.execute {
            if (!connected) { onDone(false); return@execute }
            val id = ++fileSeq
            writeLine("""{"t":"fstart","id":$id,"name":"${esc(name)}","size":${bytes.size}}""")
            val chunk = 128 * 1024
            var off = 0
            while (off < bytes.size && connected) {
                val end = minOf(off + chunk, bytes.size)
                val b64 = android.util.Base64.encodeToString(bytes.copyOfRange(off, end), android.util.Base64.NO_WRAP)
                writeLine("""{"t":"fchunk","id":$id,"data":"$b64"}""")
                off = end
                onProgress(if (bytes.isEmpty()) 100 else (off.toLong() * 100 / bytes.size).toInt())
            }
            if (connected) writeLine("""{"t":"fend","id":$id}""")
            onDone(connected)
        }
    }

    fun disconnect() {
        intentional = true
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

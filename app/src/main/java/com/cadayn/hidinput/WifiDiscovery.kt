package com.cadayn.hidinput

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import java.net.Inet4Address

/**
 * Discovers Relay desktop receivers on the LAN via mDNS / NSD (`_relay._tcp`).
 * Resolves are serialized (NsdManager only handles one reliably at a time).
 */
class WifiDiscovery(context: Context) {

    data class Host(val name: String, val ip: String, val port: Int)

    val hosts = mutableStateListOf<Host>()

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(s: String) {}
            override fun onDiscoveryStopped(s: String) {}
            override fun onStartDiscoveryFailed(s: String, e: Int) {}
            override fun onStopDiscoveryFailed(s: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { main.post { queue.addLast(info); pump() } }
            override fun onServiceLost(info: NsdServiceInfo) { main.post { hosts.removeAll { it.name == info.serviceName } } }
        }
        listener = l
        runCatching { nsd.discoverServices("_relay._tcp", NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        hosts.clear(); queue.clear(); resolving = false
    }

    private fun pump() {
        if (resolving) return
        val info = queue.removeFirstOrNull() ?: return
        resolving = true
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) { main.post { resolving = false; pump() } }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    main.post {
                        val host = i.host
                        if (host is Inet4Address) {
                            val ip = host.hostAddress
                            if (ip != null) {
                                val h = Host(i.serviceName ?: ip, ip, i.port)
                                hosts.removeAll { it.name == h.name }
                                hosts.add(h)
                            }
                        }
                        resolving = false; pump()
                    }
                }
            })
        }.onFailure { resolving = false; pump() }
    }
}

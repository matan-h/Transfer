package com.matanh.transfer

import android.content.Context
import android.net.*
import android.net.wifi.WifiManager
import timber.log.Timber
import java.math.BigInteger
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteOrder

class NetworkHelper(
    private val context: Context,
    private val onIpChanged: (newIp: String?) -> Unit
) {

    private var _lastIp: String? = null

    val currentIp: String?
        get() = _lastIp

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val logger = Timber.tag("NetworkHelper")

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkForIpChange()
        }
        override fun onLost(network: Network) {
            checkForIpChange()
        }
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            checkForIpChange()
        }
    }

    /** Start listening for IP changes */
    fun start() {
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, netCallback)
        // Immediately check once
        checkForIpChange()
    }

    /** Stop listening */
    fun stop() {
        cm.unregisterNetworkCallback(netCallback)
    }

    private fun checkForIpChange() {
        val ip = getPreferredLocalIpAddress()
        if (ip != _lastIp) {
            _lastIp = ip
            onIpChanged(ip)
        }
    }

    /**
     * Returns preferred local IP with priority:
     * 1) Wi-Fi client IP
     * 2) Tethering/hotspot IP
     * 3) null (never returns mobile data IP)
     */
    private fun getPreferredLocalIpAddress(): String? {
        // 1) Wi-Fi
        getWifiIp()?.let { return it }
        // 2) Tethering
        getTetheringIp()?.let { return it }
        // 3) no fallback
        return null
    }

    private fun getWifiIp(): String? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager? ?: return null
        if (!wm.isWifiEnabled) return null
        val info = wm.connectionInfo
        if (info.ipAddress == 0) return null
        val raw = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
            Integer.reverseBytes(info.ipAddress) else info.ipAddress
        val bytes = BigInteger.valueOf(raw.toLong()).toByteArray()
        return try {
            InetAddress.getByAddress(bytes).hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun getTetheringIp(): String? {
        NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
            val name = nif.name.lowercase()
            if (name.startsWith("ap") ||
                name.startsWith("wlan1") ||
                name.startsWith("usb") ||
                name.startsWith("rndis")
            ) {
                nif.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        logger.d("Returning address $addr for interface $name")
                        return addr.hostAddress
                    }
                }
            }
        }
        return null
    }
}

package com.matanh.transfer.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkInfo(
    val localIp: String? = null,        // e.g. 192.168.1.42
    val localHostname: String? = null,  // e.g. pixel‑2.lan
    val hotspotIp: String? = null,       // e.g. 10.0.0.1 (when acting as hotspot)
) {
    val mainIp: String?
        get() =  when {
            localIp != null -> localIp
            hotspotIp != null -> hotspotIp
            else -> null
    }
}


/**
 * A helper class to monitor network state and provide the current WiFi IP address.
 * It uses ConnectivityManager.NetworkCallback to listen for network changes.
 */
class NetworkHelper(context: Context,
                    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val applicationContext = context.applicationContext
    private val connectivityManager =
        applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkInfo  = MutableStateFlow<NetworkInfo>(NetworkInfo())
    val networkInfo : StateFlow<NetworkInfo> = _networkInfo.asStateFlow()


    private val hotspotReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            // Any change in hotspot state ⇒ re‑scan interfaces
            refresh()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network)  {refresh()}
        override fun onLost(network: Network)       {refresh()}
        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {refresh()}
    }

    init {
        // Get initial IP address on creation
        refresh()
    }

    /**
     * Registers the network callback to start listening for network changes.
     */
    fun register() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to register network callback. Missing ACCESS_NETWORK_STATE permission?")
        }
        applicationContext.registerReceiver(
            hotspotReceiver,
            IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
        )

    }

    /**
     * Unregisters the network callback to stop listening and prevent memory leaks.
     */
    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Timber.d("Network callback unregistered.")
        } catch (e: Exception) {
            // Can throw IllegalArgumentException if not registered, which is safe to ignore.
            Timber.e(e, "Error unregistering network callback.")
        }
    }

    /**
     * Fetches the current local IP address and updates the state flow.
     */
    private fun refresh() = scope.launch {
        val newLocalIp  = getWifiIp()
        val newHostname = newLocalIp?.let { findHostname(it) }
        val newHotspot  = getHotspotIP()

        val newSnapshot = NetworkInfo(
            localIp        = newLocalIp?.hostAddress,
            localHostname  = newHostname,
            hotspotIp      = newHotspot,
        )

        if (newSnapshot != _networkInfo.value) {
            _networkInfo.value = newSnapshot
            Timber.i("Network info updated → $newSnapshot")
        }
    }
    private suspend fun findHostname(addr: Inet4Address): String? = withContext(Dispatchers.IO) {
        runCatching { addr.canonicalHostName }.getOrNull()?.takeIf { it != addr.hostAddress }
    }

    /**
     * Scans network interfaces to find the device's local IPv4 address on the WiFi network.
     */
    private fun getWifiIp(): Inet4Address? {
        val networks = connectivityManager.allNetworks
        for (network in networks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val linkProperties = connectivityManager.getLinkProperties(network)
                linkProperties?.linkAddresses?.forEach { linkAddress ->
                    val ipAddress = linkAddress.address
                    // We want an IPv4 address that is not a loopback address.
                    if (ipAddress is Inet4Address && !ipAddress.isLoopbackAddress) {
                        return ipAddress
                    }
                }
            }
        }
        return null
    }
    private fun getHotspotIP(): String? {
        // TODO: also track changes like on/off
        NetworkInterface.getNetworkInterfaces().iterator().asSequence()
            .filter { ni ->
                ni.isUp &&
                        !ni.isLoopback &&
                        // common Soft-AP interface prefixes
                        (ni.name.startsWith("ap") || ni.name.startsWith("wlan") ||
                                ni.name.contains("swlan", ignoreCase = true))
            }
            .flatMap { ni -> ni.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .firstOrNull()
            ?.let { return it.hostAddress }
        return null

    }
    }

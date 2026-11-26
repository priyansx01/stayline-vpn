package io.github.priyansx01.stayline.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Tells the engine about the network underneath the VPN (Wi-Fi or mobile
 * data), and when the phone leaves deep sleep (Doze), after which the
 * tunnel's connection is usually dead.
 *
 * The VPN itself is never reported: its coming up would otherwise look
 * like a network change and make the engine rebuild a tunnel that had
 * just connected.
 */
class NetworkMonitor(private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    @Volatile
    private var current: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isVpn(network) || network == current) return
            current = network
            NativeBridge.networkChanged(network.networkHandle, true)
        }

        override fun onLost(network: Network) {
            if (network == current) {
                current = null
                NativeBridge.networkChanged(0, false)
            }
        }
    }

    private val idle = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!power.isDeviceIdleMode) NativeBridge.resumed()
        }
    }

    fun start() {
        val active = connectivity.activeNetwork?.takeUnless(::isVpn)
        current = active
        NativeBridge.networkChanged(active?.networkHandle ?: 0, active != null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The network the system would use for us without any VPN.
            val underlying = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivity.registerBestMatchingNetworkCallback(underlying, callback, Handler(Looper.getMainLooper()))
        } else {
            connectivity.registerDefaultNetworkCallback(callback)
        }
        ContextCompat.registerReceiver(
            context,
            idle,
            IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun stop() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        runCatching { context.unregisterReceiver(idle) }
    }

    private fun isVpn(network: Network): Boolean =
        connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
}

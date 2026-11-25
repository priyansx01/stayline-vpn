package io.github.priyansx01.stayline.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Tells the engine about the phone's default network, and when it leaves
 * deep sleep (Doze), after which the tunnel's connection is usually dead.
 *
 * The app is excluded from its own VPN, so the default network seen here
 * is the real one underneath.
 */
class NetworkMonitor(private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    @Volatile
    private var current: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
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
        val active = connectivity.activeNetwork
        current = active
        NativeBridge.networkChanged(active?.networkHandle ?: 0, active != null)
        connectivity.registerDefaultNetworkCallback(callback)
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
}

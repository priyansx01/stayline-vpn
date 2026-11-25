package io.github.priyansx01.stayline.vpn

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.util.Log
import io.github.priyansx01.stayline.R
import io.github.priyansx01.stayline.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the tunnel. The Rust engine does the work; this service opens the
 * VPN interface when asked and keeps the app alive while connected.
 */
class StaylineVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var monitor: NetworkMonitor

    /** A session was started here and has not ended yet. */
    private var active = false
    private var profileName: String? = null

    override fun onCreate() {
        super.onCreate()
        NativeBridge.init()
        NativeBridge.service = this
        monitor = NetworkMonitor(this).also { it.start() }
        scope.launch { TunnelRepository.state.collect(::onState) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            NativeBridge.disconnect()
            return START_STICKY
        }
        // Started as a foreground service: promise kept before anything else,
        // even if it turns out there is nothing to do.
        if (!active) goForeground(TunnelState.Connecting(1))
        when (intent?.action) {
            ACTION_CONNECT -> TunnelRepository.take()?.let(::start) ?: stopIfIdle()
            // Started by Always-on VPN, or restarted after the process died.
            else -> scope.launch { autoConnect() }
        }
        return START_STICKY
    }

    private fun start(request: ConnectRequest) {
        active = true
        profileName = request.profile.name
        Notifications.clearAttention(this)
        goForeground(TunnelState.Connecting(1))
        NativeBridge.connect(request.profile.toWire(), request.password)
    }

    private suspend fun autoConnect() {
        if (active) return
        val profile = app.profiles.settings.first().active
        val password = profile?.let { app.secrets.load(it.id) }
        if (profile == null || password == null) {
            Notifications.attention(this, getString(R.string.notif_sign_in))
            stopIfIdle()
            return
        }
        start(ConnectRequest(profile, password))
    }

    private fun onState(state: TunnelState) {
        if (!active) return
        when (state) {
            is TunnelState.NeedsUser -> {
                Notifications.attention(this, state.reason)
                finish()
            }
            TunnelState.Disconnected -> finish()
            else -> getSystemService(NotificationManager::class.java)
                .notify(Notifications.STATUS_ID, Notifications.status(this, state, profileName))
        }
    }

    private fun goForeground(state: TunnelState) {
        val notification = Notifications.status(this, state, profileName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.STATUS_ID, notification)
            return
        }
        // VPN apps may use systemExempted; fall back if this device disagrees.
        val types = listOf(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        for (type in types) {
            try {
                startForeground(Notifications.STATUS_ID, notification, type)
                return
            } catch (e: Exception) {
                Log.w(TAG, "foreground type $type refused", e)
            }
        }
    }

    private fun finish() {
        active = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopIfIdle() {
        if (!active) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Called by the engine, on its thread, once the tunnel is up. */
    fun establish(spec: LinkSpec): Int = try {
        val builder = Builder()
            .setSession(profileName ?: getString(R.string.app_name))
            .setMtu(spec.mtu)
            .addAddress(spec.address, 32)
            .addDisallowedApplication(packageName)
            .setConfigureIntent(Notifications.openApp(this))
        if (spec.fullTunnel) {
            builder.addRoute("0.0.0.0", 0)
            // The tunnel is IPv4 only: capture IPv6 too so it cannot leak
            // past the VPN. The engine drops it.
            builder.addAddress(IPV6_SINK, 128)
            builder.addRoute("::", 0)
        } else {
            spec.routes.forEach { builder.addRoute(it.address, it.prefix) }
        }
        spec.dns.forEach { builder.addDnsServer(it) }
        spec.searchDomains.forEach { builder.addSearchDomain(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.establish()?.detachFd() ?: -1
    } catch (e: Exception) {
        Log.e(TAG, "could not open the VPN interface", e)
        -1
    }

    override fun onRevoke() {
        // Another VPN took over, or the user revoked permission.
        NativeBridge.disconnect()
    }

    override fun onDestroy() {
        scope.cancel()
        monitor.stop()
        if (active) NativeBridge.disconnect()
        if (NativeBridge.service === this) NativeBridge.service = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "io.github.priyansx01.stayline.CONNECT"
        const val ACTION_DISCONNECT = "io.github.priyansx01.stayline.DISCONNECT"
        private const val TAG = "stayline"
        private const val IPV6_SINK = "fd5e:7a1e:5a7e::1"
    }
}

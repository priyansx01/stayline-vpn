package io.github.priyansx01.stayline.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.priyansx01.stayline.data.VpnProfile

/** Starting and stopping the tunnel from the UI and the tile. */
object VpnController {
    /** VPN permission must already be granted (`VpnService.prepare`). */
    fun connect(context: Context, profile: VpnProfile, password: ByteArray) {
        TunnelRepository.offer(ConnectRequest(profile, password))
        ContextCompat.startForegroundService(
            context,
            Intent(context, StaylineVpnService::class.java).setAction(StaylineVpnService.ACTION_CONNECT),
        )
    }

    fun disconnect() = NativeBridge.disconnect()
}

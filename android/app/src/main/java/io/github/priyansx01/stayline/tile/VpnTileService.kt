package io.github.priyansx01.stayline.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.priyansx01.stayline.MainActivity
import io.github.priyansx01.stayline.app
import io.github.priyansx01.stayline.vpn.TunnelRepository
import io.github.priyansx01.stayline.vpn.TunnelState
import io.github.priyansx01.stayline.vpn.VpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A Quick Settings toggle. Opens the app when it needs a password or permission. */
class VpnTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        listening = scope.launch { TunnelRepository.state.collect(::render) }
    }

    override fun onStopListening() {
        listening?.cancel()
    }

    override fun onClick() {
        if (TunnelRepository.state.value.isActive) {
            VpnController.disconnect()
            return
        }
        scope.launch {
            val profile = app.profiles.settings.first().active
            val password = profile?.let { app.secrets.load(it.id) }
            if (profile == null || password == null || VpnService.prepare(this@VpnTileService) != null) {
                password?.fill(0)
                openApp()
            } else {
                VpnController.connect(this@VpnTileService, profile, password)
            }
        }
    }

    private fun render(state: TunnelState) {
        val tile = qsTile ?: return
        tile.state = if (state.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                is TunnelState.Connected -> "Protected"
                is TunnelState.NeedsUser -> "Needs you"
                TunnelState.Disconnected -> "Off"
                else -> "Connecting…"
            }
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

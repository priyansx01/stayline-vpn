package io.github.priyansx01.stayline.vpn

import io.github.priyansx01.stayline.data.VpnProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Traffic totals plus a short history of the combined rate, for the sparkline. */
data class Traffic(
    val sent: Long = 0,
    val received: Long = 0,
    val upRate: Long = 0,
    val downRate: Long = 0,
    val samples: List<Float> = emptyList(),
) {
    fun next(sent: Long, received: Long): Traffic {
        val up = (sent - this.sent).coerceAtLeast(0) / STATS_SECONDS
        val down = (received - this.received).coerceAtLeast(0) / STATS_SECONDS
        return Traffic(sent, received, up, down, (samples + (up + down).toFloat()).takeLast(SAMPLES))
    }

    private companion object {
        /** The engine reports every two seconds. */
        const val STATS_SECONDS = 2
        const val SAMPLES = 30
    }
}

/** A connect the service picks up when it starts; the password is wiped after use. */
class ConnectRequest(val profile: VpnProfile, val password: ByteArray)

/** What the engine last said, for the UI, the service and the tile. */
object TunnelRepository {
    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(Traffic())
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    private val _certificates = MutableSharedFlow<CertificateReport>(extraBufferCapacity = 4)
    val certificates: SharedFlow<CertificateReport> = _certificates.asSharedFlow()

    private val _rejections = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val rejections: SharedFlow<String> = _rejections.asSharedFlow()

    @Volatile
    private var pending: ConnectRequest? = null

    fun offer(request: ConnectRequest) {
        pending?.password?.fill(0)
        pending = request
    }

    fun take(): ConnectRequest? = pending.also { pending = null }

    fun onEvent(event: EngineEvent) {
        when (event) {
            is EngineEvent.Status -> {
                val previous = _state.value
                if (event.state is TunnelState.Connecting && !previous.isActive) {
                    _traffic.value = Traffic()
                }
                _state.value = event.state
            }
            is EngineEvent.Stats -> _traffic.update { it.next(event.sent, event.received) }
            is EngineEvent.Certificate -> _certificates.tryEmit(event.report)
            is EngineEvent.Rejected -> _rejections.tryEmit(event.reason)
        }
    }
}

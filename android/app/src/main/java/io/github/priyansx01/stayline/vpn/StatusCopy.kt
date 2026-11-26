package io.github.priyansx01.stayline.vpn

/** What the app says about the tunnel, in words for people rather than logs. */
data class StatusCopy(val title: String, val detail: String)

/** Seconds of retrying before the app admits it is taking a while. */
private const val PATIENCE_SECONDS = 12

/**
 * Turns the engine's state into the title and line under it.
 *
 * Until the tunnel has been up once, failed attempts are part of
 * connecting: no attempt counts, no countdowns, no raw errors. Only if it
 * takes unusually long does the app say so, with a plain reason.
 */
fun statusCopy(state: TunnelState, session: Session, profileName: String?, nowMillis: Long): StatusCopy {
    val elapsed = if (session.startedAt > 0) (nowMillis - session.startedAt) / 1000 else 0
    val slow = elapsed >= PATIENCE_SECONDS
    val name = profileName ?: "your gateway"
    return when (state) {
        TunnelState.Disconnected -> StatusCopy("Not connected", "Tap the gem to connect to $name")
        is TunnelState.Connecting -> connecting(name, slow, null)
        is TunnelState.Reconnecting -> if (session.wasUp) {
            StatusCopy(
                "Reconnecting",
                if (slow) "Still trying · ${friendlyReason(state.lastError)}" else "The connection dropped. Getting you back online",
            )
        } else {
            connecting(name, slow, state.lastError)
        }
        TunnelState.WaitingForNetwork -> StatusCopy("Waiting for network", "Reconnects as soon as you are back online")
        is TunnelState.Connected -> StatusCopy("Protected", "Tunnel address ${state.localIp}")
        is TunnelState.NeedsUser -> StatusCopy("Needs you", state.reason)
    }
}

private fun connecting(name: String, slow: Boolean, lastError: String?) = StatusCopy(
    "Connecting",
    when {
        !slow -> "Securing your connection to $name"
        lastError != null -> "Taking longer than usual · ${friendlyReason(lastError)}"
        else -> "Taking longer than usual"
    },
)

/** A short, plain reason for a failed attempt; the full text is in the log. */
fun friendlyReason(error: String): String {
    val e = error.lowercase()
    return when {
        "timed out" in e || "did not answer" in e || "timeout" in e -> "the gateway is slow to answer"
        "stopped answering" in e -> "the gateway stopped answering"
        "closed the connection" in e || "ended the session" in e || "eof" in e -> "the gateway closed the connection"
        "network changed" in e || "resumed" in e -> "your network changed"
        "refused" in e || "unreachable" in e || "no route" in e || "network error" in e -> "can't reach the gateway"
        "ppp" in e || "negotiation" in e || "protocol" in e -> "the gateway didn't finish setting up"
        "adapter" in e -> "the VPN couldn't start on this phone"
        else -> "retrying"
    }
}

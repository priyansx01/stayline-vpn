package io.github.priyansx01.stayline.vpn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Mirrors `stayline_ipc::TunnelState`. */
sealed interface TunnelState {
    data object Disconnected : TunnelState
    data class Connecting(val attempt: Int) : TunnelState
    data class Connected(val localIp: String, val sinceUnix: Long) : TunnelState
    data class Reconnecting(val attempt: Int, val retryInSecs: Long, val lastError: String) : TunnelState
    data object WaitingForNetwork : TunnelState
    data class NeedsUser(val reason: String, val attention: Attention) : TunnelState

    /** Working on a tunnel, or holding one up. */
    val isActive: Boolean get() = this !is Disconnected && this !is NeedsUser
}

/** Mirrors `stayline_ipc::Attention`. */
sealed interface Attention {
    data object Other : Attention
    data object Credentials : Attention
    data class CertificateUntrusted(val fingerprint: String?) : Attention
    data class CertificateChanged(val expected: String, val actual: String) : Attention
}

data class CertificateReport(
    val gateway: String,
    val fingerprint: String?,
    val publiclyTrusted: Boolean,
    val problem: String?,
)

/** Mirrors `stayline_ipc::Event`. */
sealed interface EngineEvent {
    data class Status(val state: TunnelState) : EngineEvent
    data class Rejected(val reason: String) : EngineEvent
    data class Stats(val sent: Long, val received: Long) : EngineEvent
    data class Certificate(val report: CertificateReport) : EngineEvent
}

/** `stayline_ipc::Profile`: what the engine needs to connect. */
@Serializable
data class WireProfile(
    val gateway: String,
    val username: String,
    val realm: String? = null,
    val pin: String? = null,
)

/** `stayline_mobile::LinkSpec`: how to open the VPN interface. */
@Serializable
data class LinkSpec(
    val address: String,
    val mtu: Int,
    @SerialName("full_tunnel") val fullTunnel: Boolean,
    val routes: List<RouteSpec>,
    val dns: List<String>,
    @SerialName("search_domains") val searchDomains: List<String>,
)

@Serializable
data class RouteSpec(val address: String, val prefix: Int)

object Wire {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parseEvent(text: String): EngineEvent? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        when (o.str("type")) {
            "status" -> EngineEvent.Status(parseState(o))
            "rejected" -> EngineEvent.Rejected(o.str("reason").orEmpty())
            "stats" -> EngineEvent.Stats(o.long("sent"), o.long("received"))
            "certificate" -> EngineEvent.Certificate(
                CertificateReport(
                    gateway = o.str("gateway").orEmpty(),
                    fingerprint = o.str("fingerprint"),
                    publiclyTrusted = (o["publicly_trusted"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    problem = o.str("problem"),
                ),
            )
            else -> null
        }
    }.getOrNull()

    private fun parseState(o: JsonObject): TunnelState = when (o.str("state")) {
        "connecting" -> TunnelState.Connecting(o.long("attempt").toInt())
        "connected" -> TunnelState.Connected(o.str("local_ip").orEmpty(), o.long("since_unix"))
        "reconnecting" -> TunnelState.Reconnecting(
            o.long("attempt").toInt(),
            o.long("retry_in_secs"),
            o.str("last_error").orEmpty(),
        )
        "waiting_for_network" -> TunnelState.WaitingForNetwork
        "needs_user" -> TunnelState.NeedsUser(
            o.str("reason").orEmpty(),
            (o["attention"] as? JsonObject)?.let(::parseAttention) ?: Attention.Other,
        )
        else -> TunnelState.Disconnected
    }

    private fun parseAttention(o: JsonObject): Attention = when (o.str("kind")) {
        "credentials" -> Attention.Credentials
        "certificate_untrusted" -> Attention.CertificateUntrusted(o.str("fingerprint"))
        "certificate_changed" -> Attention.CertificateChanged(
            o.str("expected").orEmpty(),
            o.str("actual").orEmpty(),
        )
        else -> Attention.Other
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0
}

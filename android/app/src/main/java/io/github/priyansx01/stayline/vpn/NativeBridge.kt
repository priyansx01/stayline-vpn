package io.github.priyansx01.stayline.vpn

import android.util.Log

/** Called by the Rust engine (`crates/mobile/src/jni.rs`) from its own threads. */
interface NativeCallbacks {
    /** A JSON `stayline_ipc::Event`, UTF-8. */
    fun onEvent(json: ByteArray)

    /** Opens the VPN interface described by a JSON [LinkSpec]; returns its fd or -1. */
    fun establish(linkJson: ByteArray): Int
}

/** The only door into the Rust engine. Strings cross as UTF-8 bytes. */
object NativeBridge : NativeCallbacks {
    private const val TAG = "stayline"

    /** The running service, which owns `VpnService.Builder`. */
    @Volatile
    var service: StaylineVpnService? = null

    private val loaded = runCatching { System.loadLibrary("stayline_mobile") }
        .onFailure { Log.e(TAG, "could not load the tunnel engine", it) }
        .isSuccess

    @Volatile
    private var initialized = false

    @Synchronized
    fun init() {
        if (loaded && !initialized) {
            nativeInit(this)
            initialized = true
        }
    }

    /** Wipes [password] once the engine has copied it. */
    fun connect(profile: WireProfile, password: ByteArray) {
        init()
        try {
            if (loaded) nativeConnect(Wire.json.encodeToString(WireProfile.serializer(), profile).encodeToByteArray(), password)
        } finally {
            password.fill(0)
        }
    }

    fun disconnect() {
        if (initialized) nativeDisconnect() else TunnelRepository.onEvent(EngineEvent.Status(TunnelState.Disconnected))
    }

    fun inspectCertificate(gateway: String) {
        init()
        if (loaded) nativeInspectCertificate(gateway.encodeToByteArray())
    }

    fun networkChanged(handle: Long, available: Boolean) {
        if (initialized) nativeNetworkChanged(handle, available)
    }

    fun resumed() {
        if (initialized) nativeResumed()
    }

    override fun onEvent(json: ByteArray) {
        Wire.parseEvent(json.decodeToString())?.let(TunnelRepository::onEvent)
    }

    override fun establish(linkJson: ByteArray): Int {
        val spec = runCatching { Wire.json.decodeFromString(LinkSpec.serializer(), linkJson.decodeToString()) }
            .onFailure { Log.e(TAG, "bad link spec", it) }
            .getOrNull() ?: return -1
        return service?.establish(spec) ?: -1
    }

    private external fun nativeInit(callbacks: NativeCallbacks)
    private external fun nativeConnect(profileJson: ByteArray, password: ByteArray)
    private external fun nativeDisconnect()
    private external fun nativeInspectCertificate(gateway: ByteArray)
    private external fun nativeNetworkChanged(handle: Long, available: Boolean)
    private external fun nativeResumed()
}

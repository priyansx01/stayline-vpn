package io.github.priyansx01.stayline.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.priyansx01.stayline.app
import io.github.priyansx01.stayline.data.AppSettings
import io.github.priyansx01.stayline.data.ThemeMode
import io.github.priyansx01.stayline.data.VpnProfile
import io.github.priyansx01.stayline.vpn.Attention
import io.github.priyansx01.stayline.vpn.CertificateReport
import io.github.priyansx01.stayline.vpn.NativeBridge
import io.github.priyansx01.stayline.vpn.Traffic
import io.github.priyansx01.stayline.vpn.TunnelRepository
import io.github.priyansx01.stayline.vpn.TunnelState
import io.github.priyansx01.stayline.vpn.VpnController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A bottom sheet asking the user for something. */
sealed interface Sheet {
    data class Password(val profile: VpnProfile, val rejected: Boolean) : Sheet
    data class Trust(val profile: VpnProfile, val fingerprint: String, val previous: String?) : Sheet
    data object Switcher : Sheet
}

/** One-off things the screen has to do. */
sealed interface UiEvent {
    data class VpnPermission(val intent: Intent) : UiEvent
    data class Message(val text: String) : UiEvent
    data class Removed(val profile: VpnProfile, val index: Int) : UiEvent
    data object NotificationPermission : UiEvent
}

/** The profile editor; [original] is null for a new profile. */
data class Editor(val original: VpnProfile?)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.app.profiles
    private val secrets = application.app.secrets

    val settings: StateFlow<AppSettings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val tunnel: StateFlow<TunnelState> = TunnelRepository.state
    val traffic: StateFlow<Traffic> = TunnelRepository.traffic

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    var sheet by mutableStateOf<Sheet?>(null)
        private set

    /** Bumped to shake the password field. */
    var passwordShake by mutableIntStateOf(0)
        private set

    var editor by mutableStateOf<Editor?>(null)
        private set

    var inspecting by mutableStateOf(false)
        private set
    var inspected by mutableStateOf<CertificateReport?>(null)
        private set

    /** The profile of the last connect, which a "needs you" refers to. */
    private var connecting: VpnProfile? = null
    private var awaitingPermission: VpnProfile? = null
    private var autoConnectTried = false

    init {
        viewModelScope.launch { TunnelRepository.state.collect(::onState) }
        viewModelScope.launch { TunnelRepository.rejections.collect { _events.send(UiEvent.Message(it)) } }
        viewModelScope.launch {
            TunnelRepository.certificates.collect {
                inspected = it
                inspecting = false
            }
        }
    }

    private fun onState(state: TunnelState) {
        if (state !is TunnelState.NeedsUser) return
        val profile = connecting ?: settings.value?.active ?: return
        when (val attention = state.attention) {
            Attention.Credentials -> {
                // A rejected saved password would only be rejected again.
                secrets.delete(profile.id)
                sheet = Sheet.Password(profile, rejected = true)
                passwordShake++
            }
            is Attention.CertificateUntrusted -> attention.fingerprint?.let { sheet = Sheet.Trust(profile, it, null) }
            is Attention.CertificateChanged -> sheet = Sheet.Trust(profile, attention.actual, attention.expected)
            Attention.Other -> Unit
        }
    }

    /** The gem was tapped. */
    fun toggle() {
        if (tunnel.value.isActive) VpnController.disconnect() else connect(settings.value?.active)
    }

    fun connect(profile: VpnProfile?) {
        if (profile == null) {
            openEditor(null)
            return
        }
        VpnService.prepare(getApplication())?.let {
            awaitingPermission = profile
            _events.trySend(UiEvent.VpnPermission(it))
            return
        }
        val password = secrets.load(profile.id)
        if (password == null) {
            sheet = Sheet.Password(profile, rejected = false)
            return
        }
        start(profile, password)
    }

    fun onPermissionResult(granted: Boolean) {
        val profile = awaitingPermission
        awaitingPermission = null
        if (granted) {
            connect(profile)
        } else {
            _events.trySend(UiEvent.Message("Stayline needs VPN permission to connect"))
        }
    }

    /** Connects once at start-up if the user asked for it and nothing is missing. */
    fun maybeAutoConnect() {
        if (autoConnectTried) return
        autoConnectTried = true
        val s = settings.value ?: return
        val profile = s.active ?: return
        if (s.autoConnect && tunnel.value is TunnelState.Disconnected && secrets.has(profile.id) &&
            VpnService.prepare(getApplication()) == null
        ) {
            connect(profile)
        }
    }

    fun submitPassword(profile: VpnProfile, password: String, remember: Boolean) {
        val bytes = password.encodeToByteArray()
        if (remember) secrets.save(profile.id, bytes)
        sheet = null
        start(profile, bytes)
    }

    fun trust(sheet: Sheet.Trust) {
        this.sheet = null
        viewModelScope.launch {
            store.setPin(sheet.profile.id, sheet.fingerprint)
            connect(sheet.profile.copy(pin = sheet.fingerprint))
        }
    }

    private fun start(profile: VpnProfile, password: ByteArray) {
        connecting = profile
        VpnController.connect(getApplication(), profile, password)
    }

    fun showSwitcher() {
        sheet = Sheet.Switcher
    }

    fun dismissSheet() {
        sheet = null
    }

    /** Makes [profile] active; switches the tunnel over if one is up. */
    fun select(profile: VpnProfile) {
        val wasActive = settings.value?.active?.id
        viewModelScope.launch { store.setActive(profile.id) }
        if (sheet == Sheet.Switcher) sheet = null
        if (profile.id != wasActive && tunnel.value.isActive) connect(profile)
    }

    fun hasPassword(profile: VpnProfile) = secrets.has(profile.id)

    fun openEditor(profile: VpnProfile?) {
        inspected = null
        inspecting = false
        if (sheet == Sheet.Switcher) sheet = null
        editor = Editor(profile)
    }

    fun closeEditor() {
        editor = null
    }

    fun save(profile: VpnProfile, password: String, remember: Boolean) {
        viewModelScope.launch {
            store.upsert(profile)
            when {
                !remember -> secrets.delete(profile.id)
                password.isNotEmpty() -> secrets.save(profile.id, password.encodeToByteArray())
            }
            if (settings.value?.profiles.isNullOrEmpty()) store.setActive(profile.id)
            editor = null
        }
    }

    fun delete(profile: VpnProfile) {
        val index = settings.value?.profiles?.indexOfFirst { it.id == profile.id } ?: -1
        if (editor?.original?.id == profile.id) editor = null
        if (tunnel.value.isActive && settings.value?.active?.id == profile.id) VpnController.disconnect()
        viewModelScope.launch {
            store.delete(profile.id)
            _events.send(UiEvent.Removed(profile, index))
        }
    }

    /** Undoes [delete]. The saved password went with it. */
    fun restore(profile: VpnProfile, index: Int) {
        viewModelScope.launch { store.restore(profile, index) }
    }

    fun forgetPasswordIfRemoved(profile: VpnProfile) {
        if (settings.value?.profiles?.none { it.id == profile.id } == true) secrets.delete(profile.id)
    }

    fun inspect(gateway: String) {
        if (gateway.isBlank()) return
        inspected = null
        inspecting = true
        NativeBridge.inspectCertificate(gateway.trim())
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { store.setTheme(theme) }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { store.setAutoConnect(enabled) }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            store.setOnboarded()
            _events.send(UiEvent.NotificationPermission)
        }
    }
}

package io.github.priyansx01.stayline.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.priyansx01.stayline.vpn.WireProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** A saved connection; the fields match the desktop app's `[[connection]]`. */
@Serializable
data class VpnProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gateway: String,
    val username: String,
    val realm: String? = null,
    /** SHA-256 of the gateway certificate, for self-signed gateways. */
    val pin: String? = null,
) {
    fun toWire() = WireProfile(
        gateway = gateway.trim(),
        username = username.trim(),
        realm = realm?.trim()?.takeIf { it.isNotEmpty() },
        pin = pin?.trim()?.takeIf { it.isNotEmpty() },
    )
}

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val profiles: List<VpnProfile> = emptyList(),
    val activeId: String? = null,
    val theme: ThemeMode = ThemeMode.System,
    val autoConnect: Boolean = false,
    val onboarded: Boolean = false,
) {
    val active: VpnProfile? get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class ProfileStore(context: Context) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(VpnProfile.serializer())

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            profiles = prefs[PROFILES]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty(),
            activeId = prefs[ACTIVE],
            theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
            autoConnect = prefs[AUTO_CONNECT] ?: false,
            onboarded = prefs[ONBOARDED] ?: false,
        )
    }

    /** Adds or replaces [profile]; the first profile becomes the active one. */
    suspend fun upsert(profile: VpnProfile) = editProfiles { list ->
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) list.toMutableList().apply { set(index, profile) } else list + profile
    }

    /** Puts [profile] back at [index], e.g. to undo a delete. */
    suspend fun restore(profile: VpnProfile, index: Int) = editProfiles { list ->
        list.toMutableList().apply { add(index.coerceIn(0, size), profile) }
    }

    suspend fun delete(id: String) = editProfiles { list -> list.filterNot { it.id == id } }

    suspend fun setPin(id: String, pin: String?) = editProfiles { list ->
        list.map { if (it.id == id) it.copy(pin = pin) else it }
    }

    suspend fun setActive(id: String) {
        store.edit { it[ACTIVE] = id }
    }

    suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        store.edit { it[AUTO_CONNECT] = enabled }
    }

    suspend fun setOnboarded() {
        store.edit { it[ONBOARDED] = true }
    }

    private suspend fun editProfiles(change: (List<VpnProfile>) -> List<VpnProfile>) {
        store.edit { prefs ->
            val current = prefs[PROFILES]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
            val updated = change(current)
            prefs[PROFILES] = json.encodeToString(listSerializer, updated)
            if (updated.none { it.id == prefs[ACTIVE] }) {
                updated.firstOrNull()?.let { prefs[ACTIVE] = it.id } ?: prefs.remove(ACTIVE)
            }
        }
    }

    private companion object {
        val PROFILES: Preferences.Key<String> = stringPreferencesKey("profiles")
        val ACTIVE: Preferences.Key<String> = stringPreferencesKey("active")
        val THEME: Preferences.Key<String> = stringPreferencesKey("theme")
        val AUTO_CONNECT: Preferences.Key<Boolean> = booleanPreferencesKey("auto_connect")
        val ONBOARDED: Preferences.Key<Boolean> = booleanPreferencesKey("onboarded")
    }
}

package io.github.priyansx01.stayline.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Saved passwords, encrypted with an AES-GCM key that never leaves the
 * Android Keystore. Plays the part DPAPI plays on Windows.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    fun has(id: String): Boolean = prefs.contains(id)

    fun save(id: String, password: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(password)
        prefs.edit { putString(id, "${encode(cipher.iv)}:${encode(sealed)}") }
    }

    /** The password, or null if none is saved or it can no longer be read. */
    fun load(id: String): ByteArray? {
        val stored = prefs.getString(id, null) ?: return null
        return runCatching {
            val (iv, sealed) = stored.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(sealed)
        }.onFailure { Log.w("stayline", "saved password unreadable", it) }.getOrNull()
    }

    fun delete(id: String) {
        prefs.edit { remove(id) }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "stayline_passwords"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

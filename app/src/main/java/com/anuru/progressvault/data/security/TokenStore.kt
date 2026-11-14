package com.anuru.progressvault.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores API tokens encrypted with an AES-256-GCM key that lives in the Android Keystore
 * (non-exportable, never leaves secure hardware where available). Only ciphertext touches disk.
 *
 * Why not androidx.security:security-crypto (EncryptedSharedPreferences)? It's deprecated;
 * using the Keystore directly is the recommended replacement and needs no extra dependency.
 * If the key is lost (e.g. data restored to a new device), decryption fails and the token is
 * simply treated as absent — the user re-enters it.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _hasGitHubToken = MutableStateFlow(prefs.contains(KEY_GITHUB))
    val hasGitHubToken: StateFlow<Boolean> = _hasGitHubToken.asStateFlow()

    fun gitHubToken(): String? {
        val stored = prefs.getString(KEY_GITHUB, null) ?: return null
        return runCatching { decrypt(stored) }.getOrElse {
            setGitHubToken(null)
            null
        }
    }

    fun setGitHubToken(token: String?) {
        val clean = token?.trim().orEmpty()
        prefs.edit { if (clean.isEmpty()) remove(KEY_GITHUB) else putString(KEY_GITHUB, encrypt(clean)) }
        _hasGitHubToken.value = clean.isNotEmpty()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLen = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return String(cipher.doFinal(bytes, 1 + ivLen, bytes.size - 1 - ivLen), Charsets.UTF_8)
    }

    private companion object {
        const val PREFS = "secure_tokens"
        const val KEY_GITHUB = "github_token"
        const val ALIAS = "progressvault_token_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

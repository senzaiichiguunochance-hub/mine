package com.example.clipnote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keystoreの鍵は低速なため、Keystore鍵で包んだデータ鍵(AES-256)を本文の暗号化に使う。
 * 出力形式: Base64(IV 12byte + 暗号文)
 */
class CryptoManager(context: Context) {
    private val prefs = context.getSharedPreferences("clipnote_crypto", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val dataKey: SecretKey by lazy { loadOrCreateDataKey() }

    fun encrypt(plain: String): String = seal(dataKey, plain.toByteArray(Charsets.UTF_8))

    fun decrypt(blob: String): String = String(open(dataKey, blob), Charsets.UTF_8)

    private fun loadOrCreateDataKey(): SecretKey {
        val wrapKey = getOrCreateWrapKey()
        prefs.getString(KEY_WRAPPED, null)?.let { stored ->
            runCatching { SecretKeySpec(open(wrapKey, stored), "AES") }.getOrNull()?.let { return it }
        }
        val raw = ByteArray(32).also { random.nextBytes(it) }
        prefs.edit().putString(KEY_WRAPPED, seal(wrapKey, raw)).commit()
        return SecretKeySpec(raw, "AES")
    }

    private fun getOrCreateWrapKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun seal(key: SecretKey, data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.encodeToString(cipher.iv + cipher.doFinal(data), Base64.NO_WRAP)
    }

    private fun open(key: SecretKey, blob: String): ByteArray {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        return cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val WRAP_ALIAS = "clipnote_wrap_key"
        const val KEY_WRAPPED = "wrapped_data_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}

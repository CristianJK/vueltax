package com.vueltax.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clave de la base de datos SQLCipher. Se genera al azar la primera vez y se guarda cifrada
 * con una clave AES-GCM de Android Keystore, que nunca sale del hardware del dispositivo.
 */
@Singleton
class DatabasePassphraseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Synchronized
    fun get(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedPassphrase = prefs.getString(KEY_PASSPHRASE, null)
        val storedIv = prefs.getString(KEY_IV, null)
        if (storedPassphrase != null && storedIv != null) {
            return decrypt(
                encrypted = Base64.decode(storedPassphrase, Base64.NO_WRAP),
                iv = Base64.decode(storedIv, Base64.NO_WRAP),
            )
        }

        val passphrase = newPassphrase()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(passphrase)
        val saved = prefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        check(saved) { "No se pudo guardar la clave de la base de datos" }
        return passphrase
    }

    private fun decrypt(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    /** Bytes aleatorios en hexadecimal, para que la clave sea texto ASCII sin ceros. */
    private fun newPassphrase(): ByteArray {
        val random = ByteArray(PASSPHRASE_RANDOM_BYTES)
        SecureRandom().nextBytes(random)
        return random.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "database_key"
        const val KEY_PASSPHRASE = "passphrase"
        const val KEY_IV = "iv"
        const val KEY_ALIAS = "database_passphrase_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val PASSPHRASE_RANDOM_BYTES = 32
    }
}

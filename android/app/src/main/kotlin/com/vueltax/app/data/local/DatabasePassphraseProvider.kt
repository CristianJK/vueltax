package com.vueltax.app.data.local

import android.content.Context
import android.util.Base64
import android.util.Log
import com.vueltax.app.data.security.KeystoreCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
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
    private val cipher = KeystoreCipher(KEY_ALIAS)

    @Synchronized
    fun get(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_PASSPHRASE, null)
        if (stored != null) {
            try {
                return cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP))
            } catch (e: AEADBadTagException) {
                // La clave de Keystore ya no es la que cifró la contraseña (por ejemplo, el
                // sistema la borró): la base de datos es irrecuperable y se empieza de cero.
                Log.w(TAG, "Clave de la base de datos ilegible; se reinicia la base de datos", e)
                cipher.deleteKey()
                context.deleteDatabase(DATABASE_NAME)
            }
        }

        val passphrase = newPassphrase()
        val encrypted = Base64.encodeToString(cipher.encrypt(passphrase), Base64.NO_WRAP)
        val saved = prefs.edit().putString(KEY_PASSPHRASE, encrypted).commit()
        check(saved) { "No se pudo guardar la clave de la base de datos" }
        return passphrase
    }

    /** Bytes aleatorios en hexadecimal, para que la clave sea texto ASCII sin ceros. */
    private fun newPassphrase(): ByteArray {
        val random = ByteArray(PASSPHRASE_RANDOM_BYTES)
        SecureRandom().nextBytes(random)
        return random.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
    }

    companion object {
        const val DATABASE_NAME = "app.db"
        private const val TAG = "DatabasePassphrase"
        private const val PREFS_NAME = "database_key"
        private const val KEY_PASSPHRASE = "passphrase"
        private const val KEY_ALIAS = "database_passphrase_key"
        private const val PASSPHRASE_RANDOM_BYTES = 32
    }
}

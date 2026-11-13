package com.vueltax.app.capture.storage

import android.content.Context
import com.vueltax.app.data.security.KeystoreCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guarda las fotos de las facturas cifradas (AES-GCM con clave de Android Keystore) en el
 * almacenamiento interno de la app. Nunca pasan por la galería ni por almacenamiento
 * compartido, y el archivo en disco no es una imagen legible.
 */
@Singleton
class EncryptedImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cipher = KeystoreCipher(KEY_ALIAS)

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /** Cifra y guarda la imagen; devuelve el nombre con el que se recupera después. */
    suspend fun save(image: ByteArray): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}$EXTENSION"
        File(directory, name).writeBytes(cipher.encrypt(image))
        name
    }

    suspend fun read(name: String): ByteArray = withContext(Dispatchers.IO) {
        cipher.decrypt(file(name).readBytes())
    }

    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        file(name).delete()
    }

    /** Solo se aceptan nombres generados por [save], nunca rutas. */
    private fun file(name: String): File {
        require(name.endsWith(EXTENSION) && File(name).name == name) { "Nombre de imagen no válido" }
        return File(directory, name)
    }

    private companion object {
        const val KEY_ALIAS = "invoice_images_key"
        const val DIRECTORY = "invoice_images"
        const val EXTENSION = ".enc"
    }
}

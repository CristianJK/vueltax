package com.vueltax.app.capture.ocr

import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Reconoce el texto de la foto de un tiquete con ML Kit. El modelo va incluido en la app y
 * el reconocimiento se hace en el dispositivo: la imagen no se envía a ningún servidor.
 */
class ReceiptOcr @Inject constructor() {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Devuelve el texto reconocido, una línea por renglón; vacío si no se pudo leer la imagen. */
    suspend fun recognize(jpeg: ByteArray, rotationDegrees: Int): String {
        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        } ?: return ""
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        return try {
            recognizer.process(image).await().text
        } catch (e: MlKitException) {
            Log.w(TAG, "No se pudo reconocer el texto de la foto", e)
            ""
        }
    }

    private companion object {
        const val TAG = "ReceiptOcr"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}

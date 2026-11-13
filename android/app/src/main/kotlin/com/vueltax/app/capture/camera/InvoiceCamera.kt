package com.vueltax.app.capture.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

private val previewHeight = 360.dp
private val itemSpacing = 12.dp

/**
 * Vista previa de la cámara que busca códigos QR (y PDF417, que usan comprobantes peruanos
 * antiguos) en cada fotograma, con un botón para fotografiar el tiquete cuando no hay código.
 *
 * Todo ocurre en el dispositivo: ML Kit usa el modelo incluido en la app y la foto se
 * entrega en memoria como JPEG, sin pasar por la galería.
 */
@Composable
fun InvoiceCamera(
    captureLabel: String,
    onCodeScanned: (String) -> Unit,
    onPhotoTaken: (jpeg: ByteArray, rotationDegrees: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
    val currentOnPhotoTaken by rememberUpdatedState(onPhotoTaken)

    val executor = remember { ContextCompat.getMainExecutor(context) }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_PDF417)
            .build()
        BarcodeScanning.getClient(options)
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            val analyzer = MlKitAnalyzer(
                listOf(scanner),
                ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                executor,
            ) { result ->
                val code = result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue }
                if (code != null) currentOnCodeScanned(code)
            }
            setImageAnalysisAnalyzer(executor, analyzer)
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            scanner.close()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
        AndroidView(
            factory = { viewContext -> PreviewView(viewContext).also { it.controller = controller } },
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight),
        )
        Button(
            onClick = {
                val callback = PhotoCallback { jpeg, rotation -> currentOnPhotoTaken(jpeg, rotation) }
                controller.takePicture(executor, callback)
            },
        ) {
            Text(text = captureLabel)
        }
    }
}

/** Entrega la foto como bytes JPEG; si la captura falla no entrega nada. */
private class PhotoCallback(
    private val onPhoto: (ByteArray, Int) -> Unit,
) : ImageCapture.OnImageCapturedCallback() {
    override fun onCaptureSuccess(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val jpeg = ByteArray(buffer.remaining())
        buffer.get(jpeg)
        val rotation = image.imageInfo.rotationDegrees
        image.close()
        onPhoto(jpeg, rotation)
    }

    override fun onError(exception: ImageCaptureException) {
        Log.w("InvoiceCamera", "No se pudo tomar la foto", exception)
    }
}

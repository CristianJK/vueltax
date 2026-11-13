package com.vueltax.app.ui.captura

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vueltax.app.R
import com.vueltax.app.capture.camera.InvoiceCamera
import com.vueltax.domain.AppConfig

private val screenPadding = 24.dp
private val itemSpacing = 12.dp
private val chipSpacing = 8.dp

/** Añadir una factura: primero se escanea o fotografía y después se revisa. */
@Composable
fun CapturaScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CapturaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        when (state.step) {
            CapturaStep.SCANNING -> ScanStep(state = state, viewModel = viewModel, onCancel = onDone)
            CapturaStep.REVIEW -> RevisionForm(
                state = state,
                onDraftChanged = viewModel::onDraftChanged,
                onSave = viewModel::onSave,
                onDiscard = viewModel::onDiscard,
            )
        }
    }
}

@Composable
private fun ScanStep(state: CapturaUiState, viewModel: CapturaViewModel, onCancel: () -> Unit) {
    Text(
        text = stringResource(R.string.capture_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(text = stringResource(R.string.capture_country))
    CountryChips(selected = state.draft.country, onSelect = viewModel::onCountrySelected)
    Text(text = stringResource(R.string.capture_scan_hint))

    CameraWithPermission(onCodeScanned = viewModel::onQrScanned, onPhotoTaken = viewModel::onPhotoTaken)

    if (state.busy) Text(text = stringResource(R.string.capture_processing))
    if (state.unknownQr) {
        Text(
            text = stringResource(R.string.capture_qr_unknown),
            color = MaterialTheme.colorScheme.error,
        )
    }
    TextButton(onClick = viewModel::onEnterManually) {
        Text(text = stringResource(R.string.capture_enter_manually))
    }
    TextButton(onClick = onCancel) {
        Text(text = stringResource(R.string.action_back))
    }
}

@Composable
private fun CountryChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(chipSpacing)) {
        AppConfig.SUPPORTED_COUNTRIES.forEach { country ->
            FilterChip(
                selected = country == selected,
                onClick = { onSelect(country) },
                label = { Text(text = country) },
            )
        }
    }
}

/** Muestra la cámara si hay permiso; si no, lo pide y explica para qué es. */
@Composable
private fun CameraWithPermission(
    onCodeScanned: (String) -> Unit,
    onPhotoTaken: (ByteArray, Int) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        mutableStateOf(status == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted = it },
    )
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        InvoiceCamera(
            captureLabel = stringResource(R.string.capture_take_photo),
            onCodeScanned = onCodeScanned,
            onPhotoTaken = onPhotoTaken,
        )
    } else {
        Text(text = stringResource(R.string.capture_camera_permission))
        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
            Text(text = stringResource(R.string.capture_grant_permission))
        }
    }
}

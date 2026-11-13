package com.vueltax.app.ui.aeropuerto

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vueltax.app.R
import com.vueltax.app.ui.common.PlaceholderScreen

@Composable
fun ModoAeropuertoScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.airport_mode_title), modifier = modifier) {
        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.action_back))
        }
    }
}

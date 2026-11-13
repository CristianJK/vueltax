package com.vueltax.app.ui.devolucion

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vueltax.app.R
import com.vueltax.app.ui.common.PlaceholderScreen

@Composable
fun PanelDevolucionScreen(onOpenAirportMode: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.refund_title), modifier = modifier) {
        Button(onClick = onOpenAirportMode) {
            Text(text = stringResource(R.string.refund_open_airport_mode))
        }
    }
}

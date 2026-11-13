package com.vueltax.app.ui.facturas

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vueltax.app.R
import com.vueltax.app.ui.common.PlaceholderScreen

private const val SAMPLE_PURCHASE_ID = 1L

@Composable
fun FacturasScreen(onOpenPurchase: (Long) -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.purchases_title), modifier = modifier) {
        Button(onClick = { onOpenPurchase(SAMPLE_PURCHASE_ID) }) {
            Text(text = stringResource(R.string.purchases_open_sample))
        }
    }
}

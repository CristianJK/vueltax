package com.vueltax.app.ui.facturas

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vueltax.app.R
import com.vueltax.app.ui.common.PlaceholderScreen

@Composable
fun DetalleFacturaScreen(purchaseId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.purchase_detail_title),
        modifier = modifier,
    ) {
        Text(text = stringResource(R.string.purchase_detail_id, purchaseId))
        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.action_back))
        }
    }
}

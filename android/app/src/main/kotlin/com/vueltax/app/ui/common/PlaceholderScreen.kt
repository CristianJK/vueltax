package com.vueltax.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vueltax.app.R

private val screenPadding = 24.dp
private val itemSpacing = 16.dp

/** Pantalla vacía con título; cada fase la sustituirá por su contenido real. */
@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(screenPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.screen_placeholder),
            style = MaterialTheme.typography.bodyMedium,
        )
        content()
    }
}

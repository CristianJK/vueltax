package com.vueltax.app.ui.ajustes

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vueltax.app.R
import com.vueltax.app.ui.common.PlaceholderScreen

@Composable
fun AjustesScreen(modifier: Modifier = Modifier, viewModel: AjustesViewModel = hiltViewModel()) {
    PlaceholderScreen(title = stringResource(R.string.settings_title), modifier = modifier) {
        Text(text = stringResource(R.string.settings_version, viewModel.appVersion))
    }
}

package com.vueltax.app.ui.ajustes

import androidx.lifecycle.ViewModel
import com.vueltax.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AjustesViewModel @Inject constructor() : ViewModel() {
    val appVersion: String = BuildConfig.VERSION_NAME
}

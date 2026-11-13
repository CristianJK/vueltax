package com.vueltax.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.vueltax.app.R

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")

    data object MiViaje : Destination("mi_viaje")

    data object Facturas : Destination("facturas")

    data object DetalleFactura : Destination("facturas/{purchaseId}") {
        const val PURCHASE_ID = "purchaseId"

        fun route(purchaseId: Long): String = "facturas/$purchaseId"
    }

    data object Captura : Destination("captura")

    data object PanelDevolucion : Destination("panel_devolucion")

    data object Conversor : Destination("conversor")

    data object ModoAeropuerto : Destination("modo_aeropuerto")

    data object Ajustes : Destination("ajustes")
}

/** Pestaña de la barra inferior. */
data class TopLevelTab(
    val destination: Destination,
    @StringRes val label: Int,
    val icon: ImageVector,
)

val topLevelTabs = listOf(
    TopLevelTab(Destination.MiViaje, R.string.trip_title, Icons.Filled.Home),
    TopLevelTab(Destination.Facturas, R.string.purchases_title, Icons.Filled.ShoppingCart),
    TopLevelTab(Destination.PanelDevolucion, R.string.refund_tab, Icons.Filled.CheckCircle),
    TopLevelTab(Destination.Conversor, R.string.converter_title, Icons.Filled.Refresh),
    TopLevelTab(Destination.Ajustes, R.string.settings_title, Icons.Filled.Settings),
)

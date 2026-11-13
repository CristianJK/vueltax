package com.vueltax.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vueltax.app.ui.aeropuerto.ModoAeropuertoScreen
import com.vueltax.app.ui.ajustes.AjustesScreen
import com.vueltax.app.ui.captura.CapturaScreen
import com.vueltax.app.ui.conversor.ConversorScreen
import com.vueltax.app.ui.devolucion.PanelDevolucionScreen
import com.vueltax.app.ui.facturas.DetalleFacturaScreen
import com.vueltax.app.ui.facturas.FacturasScreen
import com.vueltax.app.ui.miviaje.MiViajeScreen
import com.vueltax.app.ui.onboarding.OnboardingScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelTabs.any { it.destination.route == currentRoute }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(currentRoute = currentRoute, onSelect = navController::navigateToTab)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Onboarding.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            onboardingDestination(navController)
            tabDestinations(navController)
            detailDestinations(navController)
        }
    }
}

@Composable
private fun AppBottomBar(currentRoute: String?, onSelect: (TopLevelTab) -> Unit) {
    NavigationBar {
        topLevelTabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.destination.route == currentRoute,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(text = stringResource(tab.label)) },
            )
        }
    }
}

/** Cambia de pestaña sin apilar pantallas y conservando el estado de cada una. */
private fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.destination.route) {
        popUpTo(Destination.MiViaje.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavGraphBuilder.onboardingDestination(navController: NavHostController) {
    composable(Destination.Onboarding.route) {
        OnboardingScreen(
            onStart = {
                navController.navigate(Destination.MiViaje.route) {
                    popUpTo(Destination.Onboarding.route) { inclusive = true }
                }
            },
        )
    }
}

private fun NavGraphBuilder.tabDestinations(navController: NavHostController) {
    composable(Destination.MiViaje.route) { MiViajeScreen() }
    composable(Destination.Facturas.route) {
        FacturasScreen(
            onAddPurchase = { navController.navigate(Destination.Captura.route) },
            onOpenPurchase = { id -> navController.navigate(Destination.DetalleFactura.route(id)) },
        )
    }
    composable(Destination.PanelDevolucion.route) {
        PanelDevolucionScreen(
            onOpenAirportMode = { navController.navigate(Destination.ModoAeropuerto.route) },
        )
    }
    composable(Destination.Conversor.route) { ConversorScreen() }
    composable(Destination.Ajustes.route) { AjustesScreen() }
}

private fun NavGraphBuilder.detailDestinations(navController: NavHostController) {
    composable(
        route = Destination.DetalleFactura.route,
        arguments = listOf(
            navArgument(Destination.DetalleFactura.PURCHASE_ID) { type = NavType.LongType },
        ),
    ) { entry ->
        DetalleFacturaScreen(
            purchaseId = entry.arguments?.getLong(Destination.DetalleFactura.PURCHASE_ID) ?: 0L,
            onBack = { navController.popBackStack() },
        )
    }
    composable(Destination.Captura.route) {
        CapturaScreen(onDone = { navController.popBackStack() })
    }
    composable(Destination.ModoAeropuerto.route) {
        ModoAeropuertoScreen(onBack = { navController.popBackStack() })
    }
}

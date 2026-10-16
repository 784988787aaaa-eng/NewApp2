package com.smartledger.pro.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.smartledger.pro.ui.components.AppSensoryConfig
import com.smartledger.pro.ui.screens.accounts.AccountsScreen
import com.smartledger.pro.ui.screens.dashboard.DashboardScreen
import com.smartledger.pro.ui.screens.expenses.ExpensesScreen
import com.smartledger.pro.ui.screens.inventory.InventoryScreen
import com.smartledger.pro.ui.screens.operations.VouchersScreen
import com.smartledger.pro.ui.screens.pos.PosSalesScreen
import com.smartledger.pro.ui.screens.purchases.PurchasesScreen
import com.smartledger.pro.ui.screens.reports.ReportsScreen
import com.smartledger.pro.ui.screens.settings.SettingsScreen
import com.smartledger.pro.ui.theme.SmartLedgerColors

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "الرئيسية", Icons.Filled.Home)
    object Accounts : Screen("accounts", "الحسابات", Icons.Filled.AccountBalanceWallet)
    object PosSales : Screen("pos_sales", "المبيعات", Icons.Filled.ShoppingCart)
    object Inventory : Screen("inventory", "المخزون", Icons.Filled.Inventory2)
    object Purchases : Screen("purchases", "المشتريات", Icons.Filled.LocalShipping)
    object Vouchers : Screen("vouchers", "السندات", Icons.Filled.ReceiptLong)
    object Expenses : Screen("expenses", "المصروفات", Icons.Filled.TrendingDown)
    object Reports : Screen("reports", "التقارير", Icons.Filled.Assessment)
    object Settings : Screen("settings", "الإعدادات", Icons.Filled.Settings)
}

object NavigationIntentHolder {
    var inventoryInitialFilter by mutableStateOf<String?>(null)
    var voucherInitialAccountId by mutableStateOf<Long?>(null)
    var voucherInitialIsReceipt by mutableStateOf<Boolean?>(null)
}

@Composable
fun MainAppScaffold(navController: NavHostController = rememberNavController()) {
    var privacyModeEnabled by rememberSaveable { mutableStateOf(false) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val animDuration = if (AppSensoryConfig.reduceMotion) 0 else 200

    val primaryScreens = remember {
        listOf(Screen.Dashboard, Screen.Accounts, Screen.PosSales, Screen.Inventory, Screen.Settings)
    }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        bottomBar = {
            NavigationBar(containerColor = SmartLedgerColors.SurfacePanel, tonalElevation = 6.dp) {
                primaryScreens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1) },
                        selected = isSelected,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SmartLedgerColors.AccentLight,
                            selectedTextColor = SmartLedgerColors.AccentLight,
                            indicatorColor = SmartLedgerColors.AccentSubtle,
                            unselectedIconColor = SmartLedgerColors.TextMuted,
                            unselectedTextColor = SmartLedgerColors.TextMuted
                        ),
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { if (animDuration == 0) fadeIn(tween(0)) else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(animDuration)) + fadeIn(tween(animDuration)) },
            exitTransition = { if (animDuration == 0) fadeOut(tween(0)) else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(animDuration)) + fadeOut(tween(animDuration)) },
            popEnterTransition = { if (animDuration == 0) fadeIn(tween(0)) else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(animDuration)) + fadeIn(tween(animDuration)) },
            popExitTransition = { if (animDuration == 0) fadeOut(tween(0)) else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(animDuration)) + fadeOut(tween(animDuration)) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    privacyMode = privacyModeEnabled,
                    onTogglePrivacy = { privacyModeEnabled = !privacyModeEnabled },
                    onNavigate = { route ->
                        when (route) {
                            "inventory_low_stock" -> {
                                NavigationIntentHolder.inventoryInitialFilter = "LOW_STOCK"
                                navController.navigate(Screen.Inventory.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                }
                            }
                            "voucher_receipt" -> {
                                NavigationIntentHolder.voucherInitialIsReceipt = true
                                navController.navigate(Screen.Vouchers.route)
                            }
                            "voucher_payment" -> {
                                NavigationIntentHolder.voucherInitialIsReceipt = false
                                navController.navigate(Screen.Vouchers.route)
                            }
                            else -> navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                )
            }
            composable(Screen.Accounts.route) {
                AccountsScreen(
                    privacyMode = privacyModeEnabled,
                    onOpenVoucherForAccount = { accountId, isReceipt ->
                        NavigationIntentHolder.voucherInitialAccountId = accountId
                        NavigationIntentHolder.voucherInitialIsReceipt = isReceipt
                        navController.navigate(Screen.Vouchers.route)
                    }
                )
            }
            composable(Screen.PosSales.route) { PosSalesScreen() }
            composable(Screen.Inventory.route) { InventoryScreen() }
            composable(Screen.Purchases.route) { PurchasesScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Vouchers.route) { VouchersScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Expenses.route) { ExpensesScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Reports.route) { ReportsScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

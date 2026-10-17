package com.smartledger.pro.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.navigation.NavGraph.Companion.findStartDestination
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
    object PosSales : Screen("pos_sales", "المبيعات", Icons.Filled.PointOfSale)
    object Inventory : Screen("inventory", "المخزون", Icons.Filled.Inventory2)
    object Purchases : Screen("purchases", "المشتريات", Icons.Filled.LocalShipping)
    object Vouchers : Screen("vouchers", "السندات", Icons.Filled.ReceiptLong)
    object Expenses : Screen("expenses", "المصروفات", Icons.Filled.TrendingDown)
    object Reports : Screen("reports", "التقارير", Icons.Filled.Assessment)
    object Settings : Screen("settings", "الإعدادات", Icons.Filled.Settings)
}

@Composable
fun MainAppScaffold(navController: NavHostController = rememberNavController()) {
    var privacyModeEnabled by rememberSaveable { mutableStateOf(false) }

    val primaryScreens = remember {
        listOf(
            Screen.Dashboard,
            Screen.Accounts,
            Screen.PosSales,
            Screen.Inventory,
            Screen.Settings
        )
    }

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            NavigationBar(
                containerColor = SmartLedgerColors.SurfacePanel,
                tonalElevation = 6.dp
            ) {
                primaryScreens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        },
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
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
        val durationMs = if (AppSensoryConfig.reduceMotion) 0 else 210

        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                if (durationMs == 0) EnterTransition.None
                else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(durationMs)) + fadeIn(tween(durationMs))
            },
            exitTransition = {
                if (durationMs == 0) ExitTransition.None
                else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(durationMs)) + fadeOut(tween(durationMs))
            },
            popEnterTransition = {
                if (durationMs == 0) EnterTransition.None
                else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(durationMs)) + fadeIn(tween(durationMs))
            },
            popExitTransition = {
                if (durationMs == 0) ExitTransition.None
                else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(durationMs)) + fadeOut(tween(durationMs))
            }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    privacyMode = privacyModeEnabled,
                    onTogglePrivacy = { privacyModeEnabled = !privacyModeEnabled },
                    onNavigate = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Accounts.route) {
                AccountsScreen(
                    privacyMode = privacyModeEnabled,
                    onNavigateToVouchers = { navController.navigate(Screen.Vouchers.route) }
                )
            }
            composable(Screen.PosSales.route) {
                PosSalesScreen(
                    onNavigateToInventory = {
                        navController.navigate(Screen.Inventory.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Inventory.route) {
                InventoryScreen(
                    onNavigateToPurchases = { navController.navigate(Screen.Purchases.route) }
                )
            }
            composable(Screen.Purchases.route) {
                PurchasesScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Vouchers.route) {
                VouchersScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Expenses.route) {
                ExpensesScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Reports.route) {
                ReportsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

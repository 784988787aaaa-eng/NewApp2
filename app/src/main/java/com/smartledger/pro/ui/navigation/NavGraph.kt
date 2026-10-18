package com.smartledger.pro.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    var pendingCreateAction by rememberSaveable { mutableStateOf<String?>(null) }

    fun openCreateAction(action: String) {
        pendingCreateAction = when (action) {
            "product", "purchase", "receipt", "payment", "expense", "account" -> action
            else -> null
        }
        val route = when (action) {
            "product" -> Screen.Inventory.route
            "sale" -> Screen.PosSales.route
            "purchase" -> Screen.Purchases.route
            "receipt", "payment" -> Screen.Vouchers.route
            "expense" -> Screen.Expenses.route
            "account" -> Screen.Accounts.route
            else -> return
        }
        navController.navigate(route) { launchSingleTop = true }
    }

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
        floatingActionButton = {
            GlobalCreateActionMenu(onActionSelected = ::openCreateAction)
        },
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
                    onNavigateToVouchers = { navController.navigate(Screen.Vouchers.route) },
                    createAction = pendingCreateAction,
                    onCreateActionConsumed = { pendingCreateAction = null }
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
                    onNavigateToPurchases = { navController.navigate(Screen.Purchases.route) },
                    createAction = pendingCreateAction,
                    onCreateActionConsumed = { pendingCreateAction = null }
                )
            }
            composable(Screen.Purchases.route) {
                PurchasesScreen(
                    onBack = { navController.popBackStack() },
                    createAction = pendingCreateAction,
                    onCreateActionConsumed = { pendingCreateAction = null }
                )
            }
            composable(Screen.Vouchers.route) {
                VouchersScreen(
                    onBack = { navController.popBackStack() },
                    createAction = pendingCreateAction,
                    onCreateActionConsumed = { pendingCreateAction = null }
                )
            }
            composable(Screen.Expenses.route) {
                ExpensesScreen(
                    onBack = { navController.popBackStack() },
                    createAction = pendingCreateAction,
                    onCreateActionConsumed = { pendingCreateAction = null }
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


private data class GlobalCreateOption(
    val key: String,
    val title: String,
    val icon: ImageVector
)

@Composable
private fun GlobalCreateActionMenu(
    onActionSelected: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenHeightDp < 520 -> 2
        configuration.screenWidthDp < 380 -> 2
        else -> 3
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "globalCreateRotation"
    )
    val options = remember {
        listOf(
            GlobalCreateOption("product", "صنف جديد", Icons.Filled.Inventory2),
            GlobalCreateOption("sale", "فاتورة بيع", Icons.Filled.PointOfSale),
            GlobalCreateOption("purchase", "فاتورة شراء", Icons.Filled.LocalShipping),
            GlobalCreateOption("receipt", "سند قبض", Icons.Filled.CallReceived),
            GlobalCreateOption("payment", "سند صرف", Icons.Filled.CallMade),
            GlobalCreateOption("expense", "مصروف", Icons.Filled.ReceiptLong),
            GlobalCreateOption("account", "حساب جديد", Icons.Filled.PersonAdd)
        )
    }

    BackHandler(enabled = expanded) { expanded = false }

    Box(modifier = Modifier.size(56.dp)) {
        options.forEachIndexed { index, option ->
            val row = index / columns
            val column = index % columns
            val x = -((column + 1) * 72).dp
            val y = -((row + 1) * 82).dp

            AnimatedVisibility(
                visible = expanded,
                modifier = Modifier.offset(x = x, y = y),
                enter = fadeIn(tween(190, delayMillis = index * 22)) +
                    scaleIn(animationSpec = tween(230, delayMillis = index * 22), initialScale = 0.45f),
                exit = fadeOut(tween(100)) + scaleOut(animationSpec = tween(120), targetScale = 0.65f)
            ) {
                Column(
                    modifier = Modifier.width(68.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(10.dp, CircleShape)
                            .semantics { contentDescription = option.title },
                        shape = CircleShape,
                        color = SmartLedgerColors.SurfaceCardElevated,
                        border = BorderStroke(1.dp, SmartLedgerColors.AccentPrimary.copy(alpha = 0.72f)),
                        onClick = {
                            expanded = false
                            onActionSelected(option.key)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = SmartLedgerColors.AccentLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Surface(
                        color = SmartLedgerColors.SurfacePanel.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, SmartLedgerColors.BorderSubtle)
                    ) {
                        Text(
                            text = option.title,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartLedgerColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(56.dp)
                .shadow(12.dp, CircleShape)
                .semantics { contentDescription = if (expanded) "إغلاق قائمة الإضافة" else "إضافة جديدة" },
            shape = CircleShape,
            containerColor = SmartLedgerColors.AccentPrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier
                    .size(29.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

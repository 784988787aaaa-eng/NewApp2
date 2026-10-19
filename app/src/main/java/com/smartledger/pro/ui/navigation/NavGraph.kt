package com.smartledger.pro.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.smartledger.pro.domain.model.AppFabStyle
import com.smartledger.pro.domain.model.AppNavStyle
import com.smartledger.pro.ui.components.AppSensoryConfig
import com.smartledger.pro.ui.components.FloatingModalDialog
import com.smartledger.pro.ui.components.FormSheetContainer
import com.smartledger.pro.ui.components.bounceClick
import com.smartledger.pro.ui.screens.accounts.AccountsScreen
import com.smartledger.pro.ui.screens.dashboard.DashboardScreen
import com.smartledger.pro.ui.screens.expenses.ExpensesScreen
import com.smartledger.pro.ui.screens.inventory.InventoryScreen
import com.smartledger.pro.ui.screens.operations.VouchersScreen
import com.smartledger.pro.ui.screens.pos.PosSalesScreen
import com.smartledger.pro.ui.screens.purchases.PurchasesScreen
import com.smartledger.pro.ui.screens.reports.ReportsScreen
import com.smartledger.pro.ui.screens.settings.SettingsScreen
import com.smartledger.pro.ui.theme.AppDesignState
import com.smartledger.pro.ui.theme.SmartLedgerColors
import com.smartledger.pro.ui.theme.SmartLedgerTokens

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "الرئيسية", Icons.Filled.Dashboard)
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
    var createMenuExpanded by rememberSaveable { mutableStateOf(false) }

    fun openCreateAction(action: String) {
        pendingCreateAction = when (action) {
            "product", "purchase", "receipt", "payment", "expense", "account", "treasury" -> action
            else -> null
        }
        val route = when (action) {
            "product" -> Screen.Inventory.route
            "sale" -> Screen.PosSales.route
            "purchase" -> Screen.Purchases.route
            "receipt", "payment" -> Screen.Vouchers.route
            "expense" -> Screen.Expenses.route
            "account" -> Screen.Accounts.route
            "treasury" -> Screen.Settings.route
            "reports" -> Screen.Reports.route
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = SmartLedgerColors.SurfaceWindow,
        floatingActionButton = {
            GlobalCreateActionMenu(
                expanded = createMenuExpanded,
                currentRoute = currentRoute,
                onExpandedChange = { createMenuExpanded = it },
                onActionSelected = { action ->
                    createMenuExpanded = false
                    openCreateAction(action)
                }
            )
        },
        bottomBar = {
            AdaptiveSmartBottomBar(
                screens = primaryScreens,
                currentRoute = currentRoute,
                navStyle = AppDesignState.navStyle,
                onSelectScreen = { screen ->
                    createMenuExpanded = false
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onQuickNavigateSecondary = { route ->
                    createMenuExpanded = false
                    if (currentRoute != route) {
                        navController.navigate(route) { launchSingleTop = true }
                    }
                }
            )
        }
    ) { innerPadding ->
        val durationMs = if (AppSensoryConfig.reduceMotion) 0 else 210

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                            if (route.startsWith("create:")) {
                                openCreateAction(route.removePrefix("create:"))
                            } else {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
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
                    SettingsScreen(
                        createAction = pendingCreateAction,
                        onCreateActionConsumed = { pendingCreateAction = null }
                    )
                }
            }

            if (createMenuExpanded && AppDesignState.fabStyle != AppFabStyle.COMMAND_SHEET) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SmartLedgerColors.SurfaceScrim.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { createMenuExpanded = false }
                )
            }
        }
    }
}

/**
 * شريط التنقل السفلي الذكي المتكيف مع تفضيلات المستخدم (AdaptiveSmartBottomBar):
 * 1. FLOATING_DOCK: شريط كبسولي عائم فاخر بحواف دائرية وتوهج خفيف.
 * 2. COMPACT_BAR: شريط سفلي مدمج أنيق يوفر أقصى مساحة للشاشة.
 * 3. HYBRID_PILL: شريط كبسولي هجين يتضمن اختصارات علوية سريعة للسندات والمشتريات والمصروفات والتقارير!
 */
@Composable
private fun AdaptiveSmartBottomBar(
    screens: List<Screen>,
    currentRoute: String?,
    navStyle: AppNavStyle,
    onSelectScreen: (Screen) -> Unit,
    onQuickNavigateSecondary: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (navStyle == AppNavStyle.FLOATING_DOCK) Color.Transparent
                else SmartLedgerColors.SurfacePanel
            )
    ) {
        if (navStyle == AppNavStyle.HYBRID_PILL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SmartLedgerColors.SurfaceCardElevated.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val quickLinks = listOf(
                    Triple(Screen.Vouchers.route, "السندات", Icons.Default.ReceiptLong),
                    Triple(Screen.Purchases.route, "المشتريات", Icons.Default.LocalShipping),
                    Triple(Screen.Expenses.route, "المصروفات", Icons.Default.TrendingDown),
                    Triple(Screen.Reports.route, "التقارير", Icons.Default.Assessment)
                )
                quickLinks.forEach { (route, label, icon) ->
                    val active = currentRoute == route
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(SmartLedgerTokens.chipCorner))
                            .background(if (active) SmartLedgerColors.AccentSubtle else Color.Transparent)
                            .clickable { onQuickNavigateSecondary(route) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (active) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) SmartLedgerColors.AccentLight else SmartLedgerColors.TextSecondary
                        )
                    }
                }
            }
            Divider(color = SmartLedgerColors.BorderSubtle)
        }

        val barModifier = if (navStyle == AppNavStyle.FLOATING_DOCK) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, SmartLedgerColors.BorderStrong.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
        } else {
            Modifier.fillMaxWidth()
        }

        Surface(
            modifier = barModifier,
            color = SmartLedgerColors.SurfacePanel,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = if (navStyle == AppNavStyle.COMPACT_BAR) 5.dp else 7.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                screens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) SmartLedgerColors.AccentSubtle.copy(alpha = 0.85f)
                                else Color.Transparent
                            )
                            .clickable { onSelectScreen(screen) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = if (isSelected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted,
                            modifier = Modifier.size(if (navStyle == AppNavStyle.COMPACT_BAR) 19.dp else 21.dp)
                        )
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SmartLedgerColors.AccentLight else SmartLedgerColors.TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private data class GlobalCreateOption(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color
)

@Composable
private fun GlobalCreateActionMenu(
    expanded: Boolean,
    currentRoute: String?,
    onExpandedChange: (Boolean) -> Unit,
    onActionSelected: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenHeightDp < 520 -> 2
        configuration.screenWidthDp < 380 -> 2
        else -> 3
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = if (AppSensoryConfig.reduceMotion) 0 else 180),
        label = "globalCreateRotation"
    )

    val accentLight = SmartLedgerColors.AccentLight
    val successColor = SmartLedgerColors.StateSuccess
    val dangerColor = SmartLedgerColors.StateDanger
    val warningColor = SmartLedgerColors.StateWarning

    val options = remember(accentLight, successColor, dangerColor, warningColor) {
        listOf(
            GlobalCreateOption("sale", "فاتورة بيع", "نقطة البيع POS", Icons.Filled.PointOfSale, accentLight),
            GlobalCreateOption("receipt", "سند قبض", "استلام نقدية (له)", Icons.Filled.SouthWest, successColor),
            GlobalCreateOption("payment", "سند صرف", "سداد نقدية (عليه)", Icons.Filled.NorthEast, dangerColor),
            GlobalCreateOption("purchase", "فاتورة شراء", "توريد للمخزن", Icons.Filled.LocalShipping, accentLight),
            GlobalCreateOption("product", "صنف جديد", "تعريف بالمخزون", Icons.Filled.Inventory2, warningColor),
            GlobalCreateOption("expense", "مصروف / إيراد", "نفقات التشغيل", Icons.Filled.TrendingDown, dangerColor),
            GlobalCreateOption("account", "حساب جديد", "عميل أو مورد", Icons.Filled.PersonAdd, accentLight),
            GlobalCreateOption("treasury", "الصناديق والتحويل", "خزائن ومحافظ", Icons.Filled.AccountBalanceWallet, successColor)
        )
    }

    BackHandler(enabled = expanded) { onExpandedChange(false) }

    // إذا اختار المستخدم نمط لوحة الأوامر المنبثقة (COMMAND_SHEET)
    if (expanded && AppDesignState.fabStyle == AppFabStyle.COMMAND_SHEET) {
        FloatingModalDialog(
            onDismissRequest = { onExpandedChange(false) },
            allowOutsideClickDismiss = true
        ) {
            FormSheetContainer(
                title = "لوحة الأوامر والإدخال الذكي",
                subtitle = "اختر العملية لفتح نافذة الإدخال المباشرة فوراً",
                headerIcon = Icons.Default.Bolt,
                onDismiss = { onExpandedChange(false) },
                primaryButtonText = "إغلاق اللوحة",
                onPrimaryClick = { onExpandedChange(false) }
            ) {
                options.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { opt ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceClick { onActionSelected(opt.key) },
                                shape = RoundedCornerShape(SmartLedgerTokens.cardCorner),
                                color = SmartLedgerColors.SurfaceCardElevated,
                                border = BorderStroke(1.dp, opt.tint.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(opt.tint.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(opt.icon, contentDescription = opt.title, tint = opt.tint, modifier = Modifier.size(19.dp))
                                    }
                                    Column {
                                        Text(opt.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SmartLedgerColors.TextPrimary)
                                        Text(opt.subtitle, style = MaterialTheme.typography.labelSmall, color = SmartLedgerColors.TextMuted)
                                    }
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // المدار الشعاعي أو الكبسولة السياقية المدمجة
    Box(modifier = Modifier.wrapContentSize(Alignment.BottomEnd)) {
        if (AppDesignState.fabStyle != AppFabStyle.COMMAND_SHEET) {
            Box(modifier = Modifier.size(56.dp)) {
                options.forEachIndexed { index, option ->
                    val row = index / columns
                    val column = index % columns
                    val x = -((column + 1) * 74).dp
                    val y = -((row + 1) * 82).dp
                    val motionDelay = if (AppSensoryConfig.reduceMotion) 0 else index * 20
                    val motionDuration = if (AppSensoryConfig.reduceMotion) 0 else 185

                    AnimatedVisibility(
                        visible = expanded,
                        modifier = Modifier.offset(x = x, y = y),
                        enter = fadeIn(tween(motionDuration, delayMillis = motionDelay)) +
                            scaleIn(
                                animationSpec = tween(if (AppSensoryConfig.reduceMotion) 0 else 220, delayMillis = motionDelay),
                                initialScale = if (AppSensoryConfig.reduceMotion) 1f else 0.45f
                            ),
                        exit = fadeOut(tween(if (AppSensoryConfig.reduceMotion) 0 else 100)) +
                            scaleOut(
                                animationSpec = tween(if (AppSensoryConfig.reduceMotion) 0 else 120),
                                targetScale = if (AppSensoryConfig.reduceMotion) 1f else 0.65f
                            )
                    ) {
                        Column(
                            modifier = Modifier.requiredWidth(72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(52.dp)
                                    .shadow(12.dp, RoundedCornerShape(16.dp))
                                    .semantics { contentDescription = option.title },
                                shape = RoundedCornerShape(16.dp),
                                color = SmartLedgerColors.SurfaceCardElevated,
                                border = BorderStroke(1.2.dp, option.tint.copy(alpha = 0.8f))
                            ) {
                                IconButton(
                                    onClick = { onActionSelected(option.key) },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.title,
                                        tint = option.tint,
                                        modifier = Modifier.size(23.dp)
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
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SmartLedgerColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // الزر الرئيسي (إما كبسولة ذكية سياقية أو زر + متوهج)
        if (AppDesignState.fabStyle == AppFabStyle.SMART_PILL && !expanded) {
            val contextualAction = when (currentRoute) {
                Screen.Inventory.route -> Triple("product", "صنف جديد", Icons.Filled.Inventory2)
                Screen.Accounts.route -> Triple("account", "حساب جديد", Icons.Filled.PersonAdd)
                Screen.Purchases.route -> Triple("purchase", "فاتورة شراء", Icons.Filled.LocalShipping)
                Screen.Expenses.route -> Triple("expense", "مصروف/إيراد", Icons.Filled.TrendingDown)
                Screen.Vouchers.route -> Triple("receipt", "سند قبض", Icons.Filled.SouthWest)
                else -> Triple("sale", "بيع سريع", Icons.Filled.PointOfSale)
            }
            Surface(
                modifier = Modifier.shadow(14.dp, RoundedCornerShape(50.dp)),
                shape = RoundedCornerShape(50.dp),
                color = SmartLedgerColors.SurfacePanel,
                border = BorderStroke(1.dp, SmartLedgerColors.AccentLight.copy(alpha = 0.6f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clickable { onActionSelected(contextualAction.first) }
                            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = contextualAction.third,
                            contentDescription = null,
                            tint = SmartLedgerColors.AccentLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = contextualAction.second,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SmartLedgerColors.TextPrimary
                        )
                    }
                    FloatingActionButton(
                        onClick = { onExpandedChange(true) },
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        containerColor = SmartLedgerColors.AccentPrimary,
                        contentColor = SmartLedgerColors.TextInverse
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "إضافة جديدة",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            FloatingActionButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier
                    .size(56.dp)
                    .shadow(14.dp, CircleShape)
                    .semantics { contentDescription = if (expanded) "إغلاق قائمة الإضافة" else "إضافة جديدة" },
                shape = CircleShape,
                containerColor = SmartLedgerColors.AccentPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }
    }
}

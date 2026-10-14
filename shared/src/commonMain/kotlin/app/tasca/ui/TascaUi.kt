package app.tasca.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import app.tasca.data.TxnKind
import app.tasca.ui.screens.AccountsScreen
import app.tasca.ui.screens.CategoriesScreen
import app.tasca.ui.screens.EditHoldingScreen
import app.tasca.ui.screens.EditRecurringScreen
import app.tasca.ui.screens.EditTxnScreen
import app.tasca.ui.screens.HomeScreen
import app.tasca.ui.screens.PlanScreen
import app.tasca.ui.screens.SettingsScreen
import app.tasca.ui.screens.StatsScreen
import app.tasca.ui.screens.StocksScreen
import app.tasca.ui.theme.TascaTheme
import kotlinx.coroutines.flow.first

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home),
    ACCOUNTS("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet),
    STATS("stats", "Stats", Icons.Outlined.BarChart),
    PLAN("plan", "Plan", Icons.Outlined.EventRepeat),
}

/**
 * The whole app, the same on every phone. Setting [openRoute] opens that screen, such as "txn" for the keypad or
 * "txn?id=7" for a transaction, and clears the request.
 */
@Composable
fun TascaUi(vm: AppViewModel, platform: Platform, openRoute: MutableState<String?>) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    TascaTheme(theme) { AppRoot(vm, platform, openRoute) }
}

@Composable
private fun AppRoot(vm: AppViewModel, platform: Platform, openRoute: MutableState<String?>) {
    val nav = rememberNavController()
    LaunchedEffect(openRoute.value) {
        val route = openRoute.value ?: return@LaunchedEffect
        // The NavHost sets its graph when it is first laid out, which can come after this effect starts.
        nav.currentBackStackEntryFlow.first()
        openRoute.value = null
        nav.navigate(route)
    }
    val notifications = platform.rememberNotificationAsk(onDenied = { vm.setReminder(false) })
    LaunchedEffect(Unit) {
        if (vm.reminder.value && !notifications.allowed() && vm.firstNotificationPrompt()) notifications.ask()
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            if (snackbar.showSnackbar(m.text, m.actionLabel, duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short) == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tab != null) NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME) {
                FloatingActionButton(
                    onClick = { nav.navigate("txn") },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) { Icon(Icons.Outlined.Add, "Add transaction") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = if (tab == null) padding.calculateBottomPadding() else 0.dp)) {
            NavHost(nav, startDestination = Tab.HOME.route) {
                composable(Tab.HOME.route) {
                    HomeScreen(vm, padding, onEditTxn = { nav.navigate("txn?id=$it") }, onSettings = { nav.navigate("settings") })
                }
                composable(Tab.ACCOUNTS.route) {
                    AccountsScreen(
                        vm, padding,
                        onTransfer = { nav.navigate("txn?kind=TRANSFER") },
                        onTransactions = { id ->
                            vm.showTransactions(id)
                            nav.goTab(Tab.HOME.route)
                        },
                        onInvestments = { nav.navigate("stocks") },
                        onSettings = { nav.navigate("settings") },
                    )
                }
                composable("stocks") {
                    StocksScreen(vm, { nav.popBackStack() }) { id -> nav.navigate(if (id == null) "holding" else "holding?id=$id") }
                }
                composable(
                    "holding?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) { e -> EditHoldingScreen(vm, e.id()) { nav.popBackStack() } }
                composable(Tab.STATS.route) { StatsScreen(vm, padding) { nav.navigate("settings") } }
                composable(Tab.PLAN.route) {
                    PlanScreen(vm, padding, onSettings = { nav.navigate("settings") }) { id -> nav.navigate(if (id == null) "recurring" else "recurring?id=$id") }
                }
                composable(
                    "txn?id={id}&kind={kind}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("kind") { type = NavType.StringType; defaultValue = TxnKind.EXPENSE.name },
                    ),
                ) { e ->
                    val kind = TxnKind.valueOf(e.arguments!!.read { getString("kind") })
                    EditTxnScreen(vm, platform, e.id(), kind) { nav.popBackStack() }
                }
                composable(
                    "recurring?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) { e ->
                    EditRecurringScreen(vm, e.id()) { nav.popBackStack() }
                }
                composable("settings") {
                    SettingsScreen(vm, platform, { nav.popBackStack() }, { nav.navigate("categories") })
                }
                composable("categories") { CategoriesScreen(vm) { nav.popBackStack() } }
            }
        }
    }
}

/** The "id" argument, or null for -1, which routes use for something new. */
private fun NavBackStackEntry.id(): Long? = arguments!!.read { getLong("id") }.takeIf { it >= 0 }

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

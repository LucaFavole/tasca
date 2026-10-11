package app.tasca

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tasca.data.Ledger
import app.tasca.data.TxnKind
import app.tasca.domain.Period
import app.tasca.ui.AppViewModel
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
import java.util.concurrent.TimeUnit

class TascaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "recurring",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(12, TimeUnit.HOURS).build(),
        )
    }
}

/** Records due regular payments even when the app isn't opened. */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Ledger.get(applicationContext).postDueRecurring()
        return Result.success()
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home),
    ACCOUNTS("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet),
    STATS("stats", "Stats", Icons.Outlined.BarChart),
    PLAN("plan", "Plan", Icons.Outlined.EventRepeat),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            TascaTheme(theme) { AppRoot(vm) }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

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
                    )
                }
                composable("stocks") {
                    StocksScreen(vm, { nav.popBackStack() }) { id -> nav.navigate(if (id == null) "holding" else "holding?id=$id") }
                }
                composable(
                    "holding?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) { e -> EditHoldingScreen(vm, e.arguments!!.getLong("id").takeIf { it >= 0 }) { nav.popBackStack() } }
                composable(Tab.STATS.route) { StatsScreen(vm, padding) }
                composable(Tab.PLAN.route) {
                    PlanScreen(vm, padding) { id -> nav.navigate(if (id == null) "recurring" else "recurring?id=$id") }
                }
                composable(
                    "txn?id={id}&kind={kind}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("kind") { type = NavType.StringType; defaultValue = TxnKind.EXPENSE.name },
                    ),
                ) { e ->
                    val args = e.arguments!!
                    EditTxnScreen(vm, args.getLong("id").takeIf { it >= 0 }, TxnKind.valueOf(args.getString("kind")!!)) { nav.popBackStack() }
                }
                composable(
                    "recurring?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) { e ->
                    EditRecurringScreen(vm, e.arguments!!.getLong("id").takeIf { it >= 0 }) { nav.popBackStack() }
                }
                composable("settings") {
                    SettingsScreen(vm, { nav.popBackStack() }, { nav.navigate("categories") })
                }
                composable("categories") { CategoriesScreen(vm) { nav.popBackStack() } }
            }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

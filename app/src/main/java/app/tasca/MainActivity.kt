package app.tasca

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.WorkManager
import app.tasca.data.Ledger
import app.tasca.data.get
import app.tasca.data.TxnKind
import app.tasca.domain.Money
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

class TascaApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Money.currency = getSharedPreferences("settings", Context.MODE_PRIVATE).getString("currency", "EUR")!!
        WorkManager.getInstance(this).cancelUniqueWork("recurring")
        Daily.schedule(this)
        scope.launch {
            Ledger.get(this@TascaApp).dao.ledgerChanges().debounce(500).collect { runCatching { LeftWidget.refresh(this@TascaApp) } }
        }
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
    /** Set when the widget or the reminder asks for the keypad; the UI opens it and clears the flag. */
    private val addRequested = mutableStateOf(false)
    private val listen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@registerForActivityResult
        lifecycleScope.launch {
            vm.prepareSpoken(heard)
            addRequested.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            TascaTheme(theme) { AppRoot(vm, addRequested) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val mode = QuickAdd.of(intent) ?: return
        NotificationManagerCompat.from(this).cancel(Reminder.ID)
        when (mode) {
            QuickAdd.OPEN -> Unit
            QuickAdd.KEYPAD -> addRequested.value = true
            QuickAdd.VOICE -> try {
                listen.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Es. \"15 euro pranzo\""),
                )
            } catch (_: ActivityNotFoundException) {
                addRequested.value = true
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel, addRequested: MutableState<Boolean>) {
    val nav = rememberNavController()
    LaunchedEffect(addRequested.value) {
        if (addRequested.value) { addRequested.value = false; nav.navigate("txn") }
    }
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Reminder.setOn(context, false)
    }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && Reminder.isOn(context) && !Reminder.allowed(context) && !prefs.getBoolean("askedNotifications", false)) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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

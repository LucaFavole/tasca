package app.tasca

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.tasca.data.Ledger
import app.tasca.data.get
import app.tasca.domain.Money
import app.tasca.ui.theme.TascaTheme
import app.tasca.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** Asks which account a newly placed balance widget shows; also opens when the widget is reconfigured. */
class BalanceWidgetConfig : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val done = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, done)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val accounts = Ledger.get(this).dao.balances()
        enableEdgeToEdge()
        setContent {
            val all by accounts.collectAsState(emptyList())
            TascaTheme(ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        Text("Show which account?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                        LazyColumn {
                            items(all.filter { !it.deleted }, key = { it.id }) { a ->
                                ListItem(
                                    headlineContent = { Text(a.name) },
                                    trailingContent = { Text(Money.format(a.balanceCents)) },
                                    modifier = Modifier.clickable {
                                        LeftWidget.setAccount(this@BalanceWidgetConfig, widgetId, a.id)
                                        lifecycleScope.launch {
                                            runCatching { LeftWidget.refresh(this@BalanceWidgetConfig) }
                                            setResult(RESULT_OK, done)
                                            finish()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

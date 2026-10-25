package com.qmurzik.tokskip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qmurzik.tokskip.app.ui.DatabaseScreen
import com.qmurzik.tokskip.app.ui.DumpScreen
import com.qmurzik.tokskip.app.ui.HomeScreen
import com.qmurzik.tokskip.app.ui.LogsScreen
import com.qmurzik.tokskip.app.ui.OnboardingScreen
import com.qmurzik.tokskip.app.ui.SettingsScreen
import com.qmurzik.tokskip.app.ui.StatsScreen
import com.qmurzik.tokskip.app.ui.TokSkipTheme
import com.qmurzik.tokskip.core.TokSkip

enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Filled.Home),
    DB("База", Icons.Filled.Edit),
    SETTINGS("Настройки", Icons.Filled.Settings),
    STATS("Статистика", Icons.Filled.Star),
    LOGS("Логи", Icons.AutoMirrored.Filled.List),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = TokSkip.get(this)
        setContent { TokSkipTheme { Root(app) } }
    }
}

@Composable
private fun Root(app: TokSkip) {
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var dump by rememberSaveable { mutableStateOf(false) }

    if (!settings.onboardingDone) {
        OnboardingScreen(app)
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && !dump,
                        onClick = { tab = t; dump = false },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.title, maxLines = 1) },
                    )
                }
            }
        }
    ) { pad ->
        val m = Modifier.padding(pad)
        if (dump) DumpScreen(m) { dump = false }
        else when (tab) {
            Tab.HOME -> HomeScreen(app, m, openDump = { dump = true })
            Tab.DB -> DatabaseScreen(app, m)
            Tab.SETTINGS -> SettingsScreen(app, m)
            Tab.STATS -> StatsScreen(app, m)
            Tab.LOGS -> LogsScreen(app, m)
        }
    }
}

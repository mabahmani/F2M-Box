@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.mab.f2mbox.data.CrashReporter
import com.mab.f2mbox.scraper.BaseUrlResolver
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.scraper.HtmlCache
import com.mab.f2mbox.scraper.ScraperLog
import com.mab.f2mbox.scraper.SharedPreferencesStore
import com.mab.f2mbox.navigation.F2mAppHost
import com.mab.f2mbox.ui.connect.rememberConnectSplashStrings
import com.mab.f2mbox.ui.logs.LogsScreen
import com.mab.f2mbox.ui.navigation.TvConnectedRoutes
import com.mab.f2mbox.ui.theme.F2MBoxTheme
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Capture any crash of a previous session so the logs screen can
        // show what killed the app on devices without logcat access.
        CrashReporter.install(this)
        // The html cache lives for one app open — drop whatever a previous
        // open left behind (the process can survive between launches).
        HtmlCache.clear()
        val resolver = BaseUrlResolver(BaseUrlResolver.KeyValueStorage(SharedPreferencesStore(this)))
        val scraper = F2MScraper(resolver = resolver)

        // A crash from the previous session is shown with plain Android
        // views instead of booting the app: if the crash comes from the UI
        // stack itself (e.g. R8 breaking a library), a Compose report
        // screen would crash the same way and the report would never be
        // visible.
        val pendingCrash = CrashReporter.pending(this)
        if (pendingCrash != null) {
            showCrashReport(pendingCrash) {
                CrashReporter.clear(this)
                installUi(scraper)
            }
            return
        }
        installUi(scraper)
    }

    /** The whole Compose UI: theme, RTL, connect gate and routes. */
    private fun installUi(scraper: F2MScraper) {
        setContent {
            F2MBoxTheme {
                // The whole app is RTL by default: the content is Persian and
                // every layout mirrors (rows flow right-to-left, start = right).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        colors = NonInteractiveSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        ConnectGate(scraper = scraper)
                    }
                }
            }
        }
    }

    /**
     * Crash report built from framework views only — no Compose, no tv
     * material — so it renders even when the UI stack is what crashed.
     */
    private fun showCrashReport(crash: String, onContinue: () -> Unit) {
        val button = Button(this).apply {
            text = getString(R.string.crash_continue)
            setOnClickListener { onContinue() }
        }
        val text = TextView(this).apply {
            text = crash
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(48, 16, 48, 32)
            setTextIsSelectable(true)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(48, 48, 48, 24) },
            )
            addView(text)
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }
}

/**
 * Gates the app on the local scraper engine: stays loading until a
 * healthy home page (title + hero slider) has been fetched, auto-retries
 * a few times, then shows a retry action. Every step is logged.
 */
@Composable
private fun ConnectGate(scraper: F2MScraper) {
    val context = LocalContext.current
    val splashStrings = rememberConnectSplashStrings()
    F2mAppHost(
        scraper = scraper,
        splashStrings = splashStrings,
        onSplashBack = { (context as? Activity)?.finish() },
        onConnectAttemptStart = { attempt ->
            Log.i(TAG, "connect attempt $attempt starting")
        },
        onConnectReady = { attempt ->
            Log.i(TAG, "connect attempt $attempt: home page healthy ✓")
        },
        errorLogs = { onBack -> LogsScreen(onNavigateBack = onBack) },
        connected = { session, route -> TvConnectedRoutes(session, route) },
    )
}

private const val TAG = "F2MBox"

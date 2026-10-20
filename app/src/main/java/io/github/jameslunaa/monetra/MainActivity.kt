package io.github.jameslunaa.monetra

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.monetra.reminders.Reminders
import io.github.jameslunaa.monetra.ui.lock.LockScreen
import io.github.jameslunaa.monetra.ui.nav.MonetraNavHost
import io.github.jameslunaa.monetra.ui.theme.MonetraTheme
import io.github.jameslunaa.monetra.ui.theme.isDark

/** A FragmentActivity because the fingerprint prompt needs one. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as MonetraApp).container
        val started = SystemClock.uptimeMillis()
        // Hold the logo until Records has data (capped, so a slow start never traps the user).
        installSplashScreen().setKeepOnScreenCondition {
            !container.startupReady.value && SystemClock.uptimeMillis() - started < SPLASH_MAX_MS
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.lock.start()
        val settings = container.settings.settings
        setContent {
            val s by settings.collectAsStateWithLifecycle(initialValue = null)
            val locked by container.lock.locked.collectAsStateWithLifecycle()
            val current = s ?: return@setContent
            val dark = current.themeMode.isDark()
            LaunchedEffect(dark) {
                // Every screen starts with the dark ube field, so status bar icons are always light.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            // Keep the reminder scheduled across reinstalls and reboots of the app process.
            LaunchedEffect(current.reminder, current.reminderAt) {
                if (current.reminder) Reminders.schedule(this@MainActivity, current.reminderAt) else Reminders.cancel(this@MainActivity)
            }
            MonetraTheme(current.themeMode) {
                Box {
                    MonetraNavHost()
                    if (locked && current.lockEnabled) LockScreen(container.lock, current.biometric)
                }
            }
        }
    }

    private companion object {
        const val SPLASH_MAX_MS = 4_000L
    }
}

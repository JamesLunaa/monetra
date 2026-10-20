package io.github.jameslunaa.monetra.lock

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.jameslunaa.monetra.core.security.Pin
import io.github.jameslunaa.monetra.data.prefs.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Locks the app on launch and after it has been in the background for [GRACE_MS], when a PIN is set.
 * Quick switches (copying a number from another app) don't re-prompt.
 */
class AppLock(private val settings: SettingsRepo) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _locked = MutableStateFlow(true)
    val locked = _locked.asStateFlow()
    private var backgroundedAt = 0L
    private var started = false

    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch { _locked.value = settings.settings.first().lockEnabled }
    }

    override fun onStop(owner: LifecycleOwner) { backgroundedAt = SystemClock.elapsedRealtime() }

    override fun onStart(owner: LifecycleOwner) {
        if (backgroundedAt == 0L) return
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        scope.launch {
            if (away > GRACE_MS && settings.settings.first().lockEnabled) _locked.value = true
        }
    }

    /** True and unlocks if [pin] matches; hashing runs off the main thread. */
    suspend fun tryPin(pin: String): Boolean {
        val stored = settings.settings.first().pinHash ?: return true.also { _locked.value = false }
        val ok = withContext(Dispatchers.Default) { Pin.verify(pin, stored) }
        if (ok) _locked.value = false
        return ok
    }

    fun unlockedByBiometric() { _locked.value = false }

    /** Turning the lock on from settings shouldn't lock the user out mid-session. */
    fun unlockNow() { _locked.value = false }

    companion object {
        const val GRACE_MS = 60_000L
    }
}

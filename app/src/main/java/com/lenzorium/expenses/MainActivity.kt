package com.lenzorium.expenses

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import com.lenzorium.expenses.ui.AppRoot

class MainActivity : FragmentActivity() {
    private val repo get() = (application as LenzoriumApp).repository

    /** Shared with Compose; true while the PIN screen should cover the app. */
    val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) locked.value = repo.settings.current.lockEnabled
        else locked.value = savedInstanceState.getBoolean(KEY_LOCKED, repo.settings.current.lockEnabled)
        setContent { AppRoot(repo, locked) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after 30 s in the background (short trips to the camera or file picker don't re-lock).
        if (stoppedAt != 0L && repo.settings.current.lockEnabled &&
            SystemClock.elapsedRealtime() - stoppedAt > 30_000
        ) locked.value = true
    }

    private companion object {
        const val KEY_LOCKED = "locked"
    }
}

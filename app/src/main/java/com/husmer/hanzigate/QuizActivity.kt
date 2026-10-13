package com.husmer.hanzigate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** Shown in front of X. Passing unlocks X for a while; back/home leaves X closed. */
class QuizActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = HanziApp.get(this)
        val session = app.gateSession?.takeIf { !it.passed } ?: app.newSession(app.store.needed).also {
            app.gateSession = it
        }
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    BackHandler { leave() }
                    Column(Modifier.safeDrawingPadding()) {
                        QuizScreen(
                            session = session,
                            title = "Answer ${session.needed} to open X",
                            finishLabel = "Open X",
                            onFinished = { unlock(app.store.unlockMinutes) },
                            extra = { EmergencySkip { unlock(SKIP_MINUTES) } },
                        )
                    }
                }
            }
        }
    }

    private fun unlock(minutes: Int) {
        val app = HanziApp.get(this)
        app.store.unlockedUntil = System.currentTimeMillis() + minutes * 60_000L
        app.gateSession = null
        finish() // X is right behind us
    }

    /** Leave without passing: go to the home screen instead of back into X. */
    private fun leave() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private companion object {
        const val SKIP_MINUTES = 5
        const val SKIP_WAIT_SECONDS = 30
    }

    /** A way out in case of a bug, slow enough that it isn't tempting. */
    @Composable
    private fun EmergencySkip(onSkip: () -> Unit) {
        var counting by remember { mutableStateOf(false) }
        var left by remember { mutableIntStateOf(SKIP_WAIT_SECONDS) }
        if (counting) {
            LaunchedEffect(Unit) {
                while (left > 0) { delay(1000); left-- }
                onSkip()
            }
            TextButton(onClick = { counting = false; left = SKIP_WAIT_SECONDS }) {
                Text("Skipping in ${left}s… tap to cancel", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            TextButton(onClick = { counting = true }) {
                Text("Emergency skip ($SKIP_MINUTES min)", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

package com.husmer.hanzigate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Screen { HOME, SORT, LIST, PRACTICE }

class MainActivity : ComponentActivity() {
    /** Bumped in onResume so the home screen re-checks whether the blocker is on. */
    private val resumed = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) { App(resumed.intValue) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed.intValue++
    }
}

@Composable
private fun App(resumeTick: Int) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    if (screen != Screen.HOME) BackHandler { screen = Screen.HOME }
    when (screen) {
        Screen.HOME -> Home(resumeTick) { screen = it }
        Screen.SORT -> Sorter { screen = Screen.HOME }
        Screen.LIST -> WordList()
        Screen.PRACTICE -> {
            val app = HanziApp.get(LocalContext.current)
            val session = remember { app.newSession(10) }
            QuizScreen(session, "Practice", "Done", onFinished = { screen = Screen.HOME })
        }
    }
}

@Composable
private fun Home(resumeTick: Int, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = HanziApp.get(context)
    val store = app.store
    store.version.intValue // re-read when progress changes
    val enabled = remember(resumeTick) { GateService.isEnabled(context) }
    val known = app.words.count { store.state(it.id).known }
    val unsorted = app.words.count { !store.state(it.id).sorted }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("汉字门 HanziGate", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (enabled) "✓ X blocker is on" else "X blocker is off",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (!enabled) {
                    Text(
                        "Turn on HanziGate under Accessibility → Downloaded apps. If it's greyed out, " +
                            "open App info, tap ⋮ (top right) → Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Accessibility") }
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text("App info") }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Words: $known of ${app.words.size} known", style = MaterialTheme.typography.titleMedium)
                if (known < HanziApp.MIN_KNOWN) {
                    Text(
                        "Until you mark at least ${HanziApp.MIN_KNOWN} words, the quiz uses all HSK 1 words.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(onClick = { go(Screen.SORT) }, enabled = unsorted > 0, modifier = Modifier.fillMaxWidth()) {
                    Text(if (unsorted > 0) "Sort words ($unsorted left)" else "All words sorted")
                }
                OutlinedButton(onClick = { go(Screen.LIST) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Word list & search")
                }
                OutlinedButton(onClick = { go(Screen.PRACTICE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Practice now")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Stepper("Correct answers to open X", store.needed, 1..10, 1) { store.needed = it }
                Stepper("X stays open for (minutes)", store.unlockMinutes, 5..120, 5) { store.unlockMinutes = it }
                Text(
                    "Tip: if the quiz stops appearing, set HanziGate's battery usage to Unrestricted.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(range)) }) { Text("−", fontSize = 22.sp) }
        Text("$value", Modifier.width(36.dp), fontWeight = FontWeight.Bold)
        TextButton(onClick = { onChange((value + step).coerceIn(range)) }) { Text("+", fontSize = 22.sp) }
    }
}

/** Shows unsorted words one at a time: "Know it" or "Not yet". */
@Composable
private fun Sorter(onDone: () -> Unit) {
    val app = HanziApp.get(LocalContext.current)
    val store = app.store
    store.version.intValue
    val history = remember { mutableStateListOf<String>() }
    val word = app.words.firstOrNull { !store.state(it.id).sorted }
    val sortedCount = app.words.count { store.state(it.id).sorted }

    fun mark(known: Boolean) {
        val w = word ?: return
        val s = store.state(w.id)
        store.update(w.id, s.copy(sorted = true, known = known, due = 0L))
        history.add(w.id)
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Do you know this word?", style = MaterialTheme.typography.titleMedium)
        Text("$sortedCount / ${app.words.size}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        if (word == null) {
            Text("All words sorted!", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onDone) { Text("Back") }
        } else {
            Text(word.simplified, fontSize = 72.sp)
            Text(word.pinyin, style = MaterialTheme.typography.headlineSmall)
            Text(word.meaning, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { mark(false) }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("Not yet")
                }
                Button(onClick = { mark(true) }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("Know it")
                }
            }
        }
        if (history.isNotEmpty()) {
            TextButton(onClick = {
                val id = history.removeAt(history.lastIndex)
                store.update(id, store.state(id).copy(sorted = false, known = false))
            }) { Text("Undo") }
        }
    }
}

private enum class Filter(val label: String) { ALL("All"), KNOWN("Known"), NEW("Not yet") }

/** Searchable list of all words; tap a row to mark it known or not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordList() {
    val app = HanziApp.get(LocalContext.current)
    val store = app.store
    store.version.intValue
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }

    val q = query.trim().lowercase()
    val qPinyin = Pinyin.plain(q)
    val shown = app.words.filter { w ->
        val known = store.state(w.id).known
        val passesFilter = when (filter) {
            Filter.ALL -> true
            Filter.KNOWN -> known
            Filter.NEW -> !known
        }
        passesFilter && (
            q.isEmpty() ||
                w.forms.any { q in it } ||
                q in w.meaning.lowercase() ||
                (qPinyin.isNotEmpty() && qPinyin in Pinyin.plain(w.pinyin))
            )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search: 汉字, pinyin or English") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Filter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        Text("${shown.size} words", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { w ->
                val s = store.state(w.id)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { store.update(w.id, s.copy(known = !s.known, sorted = true)) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(w.simplified, fontSize = 24.sp, modifier = Modifier.width(96.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.pinyin, style = MaterialTheme.typography.bodyLarge)
                        Text(w.meaning, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(checked = s.known, onCheckedChange = { store.update(w.id, s.copy(known = it, sorted = true)) })
                }
                HorizontalDivider()
            }
        }
    }
}

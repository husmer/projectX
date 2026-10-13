package com.husmer.hanzigate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed class Outcome {
    data class Right(val note: String?) : Outcome()
    object Missed : Outcome()
}

/**
 * Asks questions from [session] until it is passed, then shows [finishLabel] which calls [onFinished].
 * [extra] is drawn under the question (used for the emergency skip on the X gate).
 */
@Composable
fun QuizScreen(
    session: QuizSession,
    title: String,
    finishLabel: String,
    onFinished: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val app = HanziApp.get(LocalContext.current)
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }

    var question by remember { mutableStateOf(session.next(System.currentTimeMillis())) }
    var questionNo by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }

    fun settle(ok: Boolean, note: String? = null) {
        val now = System.currentTimeMillis()
        val w = question.word
        session.record(w, ok)
        val s = app.store.state(w.id)
        app.store.update(w.id, if (ok) Srs.correct(s, now) else Srs.wrong(s, now))
        outcome = if (ok) Outcome.Right(note) else Outcome.Missed
        hint = null
        speaker.say(question.sentence?.full ?: w.simplified)
    }

    fun submit() {
        if (outcome != null) return
        val q = question
        val verdict = when (q.type) {
            QuestionType.ENGLISH_TO_HANZI -> app.checker.checkHanzi(input, q.word)
            QuestionType.HANZI_TO_PINYIN -> app.checker.checkPinyin(input, q.word)
            QuestionType.SENTENCE -> app.checker.checkSentence(input, q.word, q.sentence!!)
        }
        when (verdict) {
            Verdict.Correct -> settle(true)
            is Verdict.CorrectNote -> settle(true, verdict.note)
            is Verdict.TryAgain -> hint = verdict.hint
            Verdict.Wrong -> settle(false)
        }
    }

    fun next() {
        question = session.next(System.currentTimeMillis())
        questionNo++
        input = ""
        outcome = null
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { (session.correctCount.toFloat() / session.needed).coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${session.correctCount.coerceAtMost(session.needed)} / ${session.needed} correct",
            style = MaterialTheme.typography.bodySmall,
        )

        Prompt(question)

        val done = outcome
        if (done == null) {
            key(questionNo) {
                AnswerField(
                    chinese = question.type != QuestionType.HANZI_TO_PINYIN,
                    onChange = { input = it; hint = null },
                    onSubmit = ::submit,
                )
            }
            hint?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ::submit) { Text("Check") }
                OutlinedButton(onClick = { settle(false) }) { Text("I don't know") }
            }
        } else {
            Reveal(question, done, speaker)
            if (session.passed) {
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text(finishLabel) }
            } else {
                Button(onClick = ::next, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            }
        }
        Spacer(Modifier.height(8.dp))
        extra()
    }
}

@Composable
private fun Prompt(q: Question) {
    val (label, big, small) = when (q.type) {
        QuestionType.ENGLISH_TO_HANZI -> Triple(
            "Write in Chinese",
            q.word.meaning,
            "${q.word.simplified.length} character" + if (q.word.simplified.length > 1) "s" else "",
        )
        QuestionType.HANZI_TO_PINYIN -> Triple(
            "Type the pinyin (tone numbers: ni3 hao3)",
            q.word.simplified,
            null,
        )
        QuestionType.SENTENCE -> Triple("Fill in the blank", q.sentence!!.withBlank, q.sentence!!.english)
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(
        big,
        fontSize = if (q.type == QuestionType.HANZI_TO_PINYIN) 56.sp else 28.sp,
        lineHeight = if (q.type == QuestionType.HANZI_TO_PINYIN) 64.sp else 38.sp,
        fontWeight = FontWeight.Medium,
    )
    small?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun Reveal(q: Question, outcome: Outcome, speaker: Speaker) {
    val right = outcome is Outcome.Right
    val color = if (right) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (right) "✓ Correct" else "✗ Not quite", color = color, fontWeight = FontWeight.Bold)
            (outcome as? Outcome.Right)?.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.word.simplified, fontSize = 40.sp, modifier = Modifier.padding(end = 16.dp))
                Column {
                    Text(q.word.pinyin, style = MaterialTheme.typography.titleMedium)
                    Text(q.word.meaning, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (q.word.traditional != q.word.simplified) {
                Text("Traditional: ${q.word.traditional}", style = MaterialTheme.typography.bodySmall)
            }
            q.sentence?.let {
                Text(it.full, fontSize = 20.sp)
                Text(it.english, style = MaterialTheme.typography.bodySmall)
            }
            if (speaker.ready.value) {
                TextButton(onClick = { speaker.say(q.sentence?.full ?: q.word.simplified) }) { Text("🔊 Listen") }
            }
        }
    }
    if (!right) {
        Text(
            "This word will come back in a moment.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
        )
    }
}

package com.husmer.hanzigate

import kotlin.random.Random

enum class QuestionType { ENGLISH_TO_HANZI, HANZI_TO_PINYIN, SENTENCE }

data class Question(val type: QuestionType, val word: Word, val sentence: Sentence?)

/** What the app remembers about one word. */
data class WordState(
    val known: Boolean = false,
    val sorted: Boolean = false,
    val box: Int = 0,
    val due: Long = 0L,
)

object Srs {
    private const val HOUR = 60 * 60 * 1000L
    private const val DAY = 24 * HOUR

    /** Wait before a word comes back after reaching box n. */
    private val waits = longArrayOf(0, HOUR, DAY, 3 * DAY, 7 * DAY, 21 * DAY)
    const val MAX_BOX = 5

    fun correct(s: WordState, now: Long): WordState {
        val box = (s.box + 1).coerceAtMost(MAX_BOX)
        return s.copy(box = box, due = now + waits[box])
    }

    fun wrong(s: WordState, now: Long): WordState = s.copy(box = 0, due = now)
}

/**
 * One round of questions. Words answered wrong come back two questions later,
 * and the round is passed after [needed] correct answers.
 */
class QuizSession(
    private val pool: List<Word>,
    private val sentences: Map<String, List<Sentence>>,
    private val state: (String) -> WordState,
    val needed: Int,
    private val random: Random = Random.Default,
) {
    var correctCount = 0
        private set
    val passed get() = correctCount >= needed

    private val retry = ArrayDeque<Pair<Word, Int>>() // word, questions to wait
    private val recent = ArrayDeque<String>()

    fun next(now: Long): Question {
        val word = takeRetry() ?: pickWord(now)
        recent.addLast(word.id)
        while (recent.size > minOf(5, pool.size - 1).coerceAtLeast(0)) recent.removeFirst()
        return Question(pickType(word), word, null).let { q ->
            if (q.type == QuestionType.SENTENCE) q.copy(sentence = sentences[word.id]!!.random(random)) else q
        }
    }

    fun record(word: Word, ok: Boolean) {
        if (ok) correctCount++ else retry.addLast(word to 2)
    }

    private fun takeRetry(): Word? {
        val ready = retry.firstOrNull { it.second <= 0 }
        val result = ready?.first
        if (ready != null) retry.remove(ready)
        for (i in retry.indices) retry[i] = retry[i].first to retry[i].second - 1
        return result
    }

    private fun pickWord(now: Long): Word {
        val fresh = pool.filter { it.id !in recent }.ifEmpty { pool }
        val due = fresh.filter { state(it.id).due <= now }
        if (due.isNotEmpty()) {
            // Lowest box first, with a little shuffling so it isn't always the same order.
            val lowest = due.minOf { state(it.id).box }
            return due.filter { state(it.id).box <= lowest + 1 }.random(random)
        }
        // Nothing due: favour weaker words.
        val weights = fresh.map { 1.0 / (state(it.id).box + 1) }
        var r = random.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) return fresh[i]
        }
        return fresh.last()
    }

    private fun pickType(word: Word): QuestionType {
        val types = buildList {
            add(QuestionType.ENGLISH_TO_HANZI); add(QuestionType.ENGLISH_TO_HANZI)
            add(QuestionType.HANZI_TO_PINYIN)
            if (!sentences[word.id].isNullOrEmpty()) { add(QuestionType.SENTENCE); add(QuestionType.SENTENCE) }
        }
        return types.random(random)
    }
}

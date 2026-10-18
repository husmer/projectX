package com.husmer.hanzigate

import android.app.Application
import android.content.Context

class HanziApp : Application() {
    val words: List<Word> by lazy { WordParser.parseWords(asset("hsk1_2021.tsv")) }
    val sentences: Map<String, List<Sentence>> by lazy {
        WordParser.parseSentences(asset("hsk1_sentences.tsv")).groupBy { it.wordId }
    }
    /** Category names in display order. */
    val categories: List<String> by lazy { asset("hsk1_categories.txt").lines().filter { it.isNotBlank() } }
    val checker: AnswerChecker by lazy { AnswerChecker(words) }
    val store: Store by lazy { Store(this) }

    /** The quiz in front of X, kept here so switching away and back doesn't restart it. */
    var gateSession: QuizSession? = null

    /** Words the quiz draws from: the ones marked as known, or everything until enough are marked. */
    fun quizPool(): List<Word> {
        val known = words.filter { store.state(it.id).known }
        return if (known.size >= MIN_KNOWN) known else words
    }

    fun wordsIn(category: String) = words.filter { it.category == category }

    fun newSession(needed: Int) = QuizSession(quizPool(), sentences, store::state, needed)

    private fun asset(name: String) = assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        const val MIN_KNOWN = 5
        fun get(context: Context) = context.applicationContext as HanziApp
    }
}

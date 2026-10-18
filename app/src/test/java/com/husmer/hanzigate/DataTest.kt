package com.husmer.hanzigate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DataTest {
    // Unit tests run from the app/ module directory.
    private val words = WordParser.parseWords(File("../wordlists/hsk1_2021.tsv").readText())
    private val sentences = WordParser.parseSentences(File("../wordlists/hsk1_sentences.tsv").readText())

    @Test
    fun wordListIsComplete() {
        assertEquals(500, words.size)
        assertEquals(500, words.map { it.id }.toSet().size)
        assertTrue(words.all { it.meaning.isNotBlank() && it.pinyin.isNotBlank() })
    }

    @Test
    fun everyWordIsInAListedCategory() {
        val categories = File("../wordlists/hsk1_categories.txt").readLines().filter { it.isNotBlank() }
        assertEquals(categories.size, categories.toSet().size)
        for (w in words) assertTrue("${w.id} ${w.simplified}: category '${w.category}'", w.category in categories)
        assertEquals(categories.toSet(), words.map { it.category }.toSet())
    }

    @Test
    fun everyWordHasAMatchingSentence() {
        val byId = words.associateBy { it.id }
        assertEquals(500, sentences.size)
        for (s in sentences) {
            val w = byId[s.wordId] ?: error("unknown id ${s.wordId}")
            assertTrue("${s.wordId}: 【${s.answer}】 is not a form of ${w.simplified}", s.answer in w.forms)
            assertTrue("${s.wordId}: empty translation", s.english.isNotBlank())
        }
        assertEquals(words.map { it.id }.toSet(), sentences.map { it.wordId }.toSet())
    }
}

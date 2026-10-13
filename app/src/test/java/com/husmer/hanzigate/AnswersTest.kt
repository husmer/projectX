package com.husmer.hanzigate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AnswersTest {
    private fun w(id: String, s: String, p: String, m: String, also: List<String> = emptyList(), t: String = s) =
        Word(id, s, t, p, m, "", also)

    private val drink = w("1", "喝", "hē", "to drink")
    private val dad = w("2", "爸爸", "bàba", "dad; father", listOf("爸"))
    private val forget = w("3", "忘", "wàng", "to forget")
    private val forget2 = w("4", "忘记", "wàngjì", "to forget")
    private val some = w("5", "一些", "yīxiē", "some")
    private val someOf = w("6", "有的", "yǒude", "some; some of them")
    private val woman = w("7", "女", "nǚ", "female")
    private val who = w("8", "谁", "shéi/shuí", "who")
    private val yixia = w("9", "一下儿", "yīxiàr", "once")
    private val back = w("10", "后", "hòu", "after; behind", t = "後")
    private val all = listOf(drink, dad, forget, forget2, some, someOf, woman, who, yixia, back)
    private val checker = AnswerChecker(all)

    @Test
    fun hanzi() {
        assertEquals(Verdict.Correct, checker.checkHanzi(" 喝 ", drink))
        assertEquals(Verdict.Correct, checker.checkHanzi("爸", dad))
        assertEquals(Verdict.Correct, checker.checkHanzi("後", back))
        assertEquals(Verdict.Wrong, checker.checkHanzi("吃", drink))
        assertTrue(checker.checkHanzi("忘记", forget) is Verdict.CorrectNote)
        assertTrue(checker.checkHanzi("有的", some) is Verdict.TryAgain)
        assertTrue(checker.checkHanzi("he1", drink) is Verdict.TryAgain)
        assertTrue(checker.checkHanzi("", drink) is Verdict.TryAgain)
    }

    @Test
    fun pinyin() {
        assertEquals(Verdict.Correct, checker.checkPinyin("he1", drink))
        assertEquals(Verdict.Correct, checker.checkPinyin("hē", drink))
        assertEquals(Verdict.Wrong, checker.checkPinyin("he2", drink))
        assertTrue(checker.checkPinyin("he", drink) is Verdict.TryAgain)
        assertEquals(Verdict.Correct, checker.checkPinyin("ba4ba5", dad))
        assertEquals(Verdict.Correct, checker.checkPinyin("ba4 ba", dad))
        assertEquals(Verdict.Correct, checker.checkPinyin("nv3", woman))
        assertEquals(Verdict.Correct, checker.checkPinyin("nü3", woman))
        assertEquals(Verdict.Correct, checker.checkPinyin("shui2", who))
        assertEquals(Verdict.Correct, checker.checkPinyin("Shei2", who))
        assertTrue(checker.checkPinyin("yi2xiar4", yixia) is Verdict.CorrectNote)
    }

    @Test
    fun sentenceAcceptsBlankForm() {
        val s = Sentence("2", "我", "爸", "是医生。", "My dad is a doctor.")
        assertEquals(Verdict.Correct, checker.checkSentence("爸", dad, s))
        assertEquals(Verdict.Correct, checker.checkSentence("爸爸", dad, s))
    }

    @Test
    fun sessionRepeatsMissedWordsAndPasses() {
        val pool = listOf(drink, dad, forget, some, woman)
        val session = QuizSession(pool, emptyMap(), { WordState(known = true) }, needed = 3, random = Random(1))
        val first = session.next(0).word
        session.record(first, ok = false)
        val seen = (1..3).map { session.next(0).word.also { w -> session.record(w, ok = true) } }
        assertTrue("missed word should come back", first in seen)
        assertTrue(session.passed)
    }

    @Test
    fun srsBoxes() {
        val s1 = Srs.correct(WordState(), now = 0)
        assertEquals(1, s1.box)
        assertTrue(s1.due > 0)
        assertEquals(0, Srs.wrong(s1, 5).box)
    }
}

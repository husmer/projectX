package com.husmer.hanzigate

sealed class Verdict {
    object Correct : Verdict()
    /** Right, with a remark worth showing (e.g. an accepted alternative). */
    data class CorrectNote(val note: String) : Verdict()
    /** Not counted as wrong; the user may try again. */
    data class TryAgain(val hint: String) : Verdict()
    object Wrong : Verdict()
}

class AnswerChecker(private val all: List<Word>) {
    private val byForm: Map<String, List<Word>> =
        all.flatMap { w -> w.forms.map { it to w } }.groupBy({ it.first }, { it.second })

    private fun clean(input: String) =
        input.filterNot { it.isWhitespace() || it in "。，、.,!！?？\"'“”" }

    fun checkHanzi(input: String, target: Word): Verdict {
        val a = clean(input)
        if (a.isEmpty()) return Verdict.TryAgain("Type an answer first.")
        if (a in target.forms) return Verdict.Correct
        if (a.all { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() }) {
            return Verdict.TryAgain("Switch your keyboard to Chinese (Pinyin) and pick the characters.")
        }
        val others = byForm[a].orEmpty()
        // Different word with exactly the same gloss (忘 / 忘记): fully correct.
        if (others.any { it.meaning == target.meaning }) {
            return Verdict.CorrectNote("Also right! Expected ${target.simplified}.")
        }
        val overlap = others.firstOrNull { o -> o.meaningParts.any { it in target.meaningParts } }
        if (overlap != null) {
            return Verdict.TryAgain(
                "${overlap.simplified} also means \"${overlap.meaning}\", but I'm thinking of " +
                    "a ${target.simplified.length}-character word."
            )
        }
        return Verdict.Wrong
    }

    fun checkSentence(input: String, target: Word, sentence: Sentence): Verdict {
        val a = clean(input)
        if (a == sentence.answer) return Verdict.Correct
        return checkHanzi(input, target)
    }

    fun checkPinyin(input: String, target: Word): Verdict {
        if (input.isBlank()) return Verdict.TryAgain("Type the pinyin first.")
        val got = Pinyin.key(input)
        val options = target.pinyin.split('/').map { Pinyin.key(it) }
        if (options.any { it == got }) return Verdict.Correct
        val sameLetters = options.firstOrNull { it.letters == got.letters }
        if (sameLetters != null) {
            if (got.tones.isEmpty()) {
                return Verdict.TryAgain("Add a tone number after each syllable, like ni3 hao3.")
            }
            // 一 and 不 change tone in speech (yí ge, bú shì); accept the spoken tone.
            if (target.simplified.any { it == '一' || it == '不' } && got.tones.length == sameLetters.tones.length) {
                return Verdict.CorrectNote("The dictionary form is ${target.pinyin}.")
            }
        }
        return Verdict.Wrong
    }
}

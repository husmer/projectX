package com.husmer.hanzigate

import java.text.Normalizer

/**
 * Compares pinyin typed with tone numbers ("ni3 hao3") or tone marks ("nǐ hǎo").
 * Each spelling is reduced to its letters plus the sequence of tones (neutral tone dropped),
 * so "bàba", "ba4ba5" and "ba4 ba" all become ("baba", "4").
 */
object Pinyin {
    private val marked = mapOf(
        'ā' to "a1", 'á' to "a2", 'ǎ' to "a3", 'à' to "a4",
        'ē' to "e1", 'é' to "e2", 'ě' to "e3", 'è' to "e4",
        'ī' to "i1", 'í' to "i2", 'ǐ' to "i3", 'ì' to "i4",
        'ō' to "o1", 'ó' to "o2", 'ǒ' to "o3", 'ò' to "o4",
        'ū' to "u1", 'ú' to "u2", 'ǔ' to "u3", 'ù' to "u4",
        'ǖ' to "u1", 'ǘ' to "u2", 'ǚ' to "u3", 'ǜ' to "u4", 'ü' to "u",
    )

    data class Key(val letters: String, val tones: String)

    fun key(text: String): Key {
        val letters = StringBuilder()
        val tones = StringBuilder()
        for (ch in Normalizer.normalize(text.lowercase(), Normalizer.Form.NFC)) {
            val m = marked[ch]
            when {
                m != null -> {
                    letters.append(m[0])
                    if (m.length > 1) tones.append(m[1])
                }
                ch in '1'..'4' -> tones.append(ch)
                ch == 'v' -> letters.append('u') // "nv3" is a common way to type nǚ
                ch in 'a'..'z' -> letters.append(ch)
                else -> Unit // spaces, apostrophes, "5"/"0" for neutral tone, "u:" etc.
            }
        }
        return Key(letters.toString(), tones.toString())
    }

    /** Letters only, for searching ("hao" finds 好). */
    fun plain(text: String): String = key(text).letters
}

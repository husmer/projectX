package com.husmer.hanzigate

data class Word(
    val id: String,
    val simplified: String,
    val traditional: String,
    val pinyin: String,
    val meaning: String,
    val pos: String,
    val also: List<String>,
    /** Topic group, so known words can be chosen a group at a time. */
    val category: String = "",
) {
    /** Every way of writing this word that counts as a correct answer. */
    val forms: Set<String> get() = setOf(simplified, traditional) + also

    val meaningParts: List<String>
        get() = meaning.split(';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}

/** A sentence that uses [wordId]; [answer] is the bracketed part that gets blanked out. */
data class Sentence(
    val wordId: String,
    val before: String,
    val answer: String,
    val after: String,
    val english: String,
) {
    val withBlank: String get() = "$before ＿＿ $after".trim()
    val full: String get() = before + answer + after
}

object WordParser {
    fun parseWords(tsv: String): List<Word> =
        tsv.lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val c = line.split('\t')
                Word(
                    id = c[0],
                    simplified = c[1],
                    traditional = c[2],
                    pinyin = c[3],
                    meaning = c[4],
                    pos = c.getOrElse(5) { "" },
                    also = c.getOrElse(6) { "" }.split('/').map { it.trim() }.filter { it.isNotEmpty() },
                    category = c.getOrElse(7) { "" },
                )
            }
            .toList()

    private val bracket = Regex("^(.*?)【(.+?)】(.*)$")

    /** Lines are `id<TAB>sentence with 【answer】<TAB>english`. */
    fun parseSentences(tsv: String): List<Sentence> =
        tsv.lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val c = line.split('\t')
                if (c.size < 3) return@mapNotNull null
                val m = bracket.find(c[1]) ?: return@mapNotNull null
                Sentence(c[0], m.groupValues[1], m.groupValues[2], m.groupValues[3], c[2])
            }
            .toList()
}

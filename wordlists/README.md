# Word lists

## `hsk1_2021.tsv` — New HSK (HSK 3.0, 2021) Level 1

The 500 Level-1 words from the official 2021 standard
*Chinese Proficiency Grading Standards for International Chinese Language Education* (GF0025-2021).

Tab-separated, UTF-8, one word per line:

| column | meaning |
|---|---|
| `id` | position in the official list (`L1-0001` … `L1-0500`) |
| `simplified` / `traditional` | characters |
| `pinyin` | with tone marks |
| `meaning` | short English gloss, written for quizzing (first/common sense only) |
| `pos` | part of speech from the official list (blank for phrases) |
| `also` | shorter form listed in the same entry (e.g. 爸爸 → 爸) |

Sources: word list, pinyin and part of speech from
[ivankra/hsk30](https://github.com/ivankra/hsk30) (a transcription of the official PDF),
cross-checked against [drkameleon/complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary).
English glosses written by hand.

Note: Chinese Testing International revised the exam syllabus again in late 2025,
and some sources say the exam's HSK 1 is now ~300 words. This file is the 2021 standard.

## `jlpt_n5.tsv` — JLPT N5 (Japanese), for KotobaGate

660 words at the JLPT N5 level. There is no official JLPT word list; this one starts from
[jamsinclair/open-anki-jlpt-decks](https://github.com/jamsinclair/open-anki-jlpt-decks) (MIT),
itself based on Jonathan Waller's lists at tanos.co.uk. Counters and suffixes (～円, ～枚…),
dated items (テープレコーダー, 灰皿…) and duplicates (有る/在る) were dropped, and the
English glosses were rewritten by hand for quizzing.

Tab-separated, UTF-8, one word per line:

| column | meaning |
|---|---|
| `id` | `N5-0001` …, ordered by category |
| `word` | the usual written form (kanji, or kana where kana is normal) |
| `kana` | reading(s) in kana, `/`-separated (九 → `きゅう/く`) |
| `meaning` | short English gloss; brackets tell apart same-meaning words (暑い "hot (weather)", 熱い "hot (to the touch)") |
| `also` | other accepted spellings, `/`-separated (おいしい → `美味しい`) |
| `category` | one of 26 topic groups (Food & drink, Verbs: coming & going…), so known words can be marked a group at a time |

`jlpt_n5_sentences.tsv`: one hand-written example sentence per word, with the word in 【】,
always in a form listed for that word (verbs and adjectives in dictionary form), plus an English translation.

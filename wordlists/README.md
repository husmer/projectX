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
| `hellochinese_topic` | first topic in the old HelloChinese list containing the word (blank if not in it) |

303 of the 500 words appear somewhere in the old HelloChinese list.

Sources: word list, pinyin and part of speech from
[ivankra/hsk30](https://github.com/ivankra/hsk30) (a transcription of the official PDF),
cross-checked against [drkameleon/complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary).
English glosses written by hand.

Note: Chinese Testing International revised the exam syllabus again in late 2025,
and some sources say the exam's HSK 1 is now ~300 words. This file is the 2021 standard.

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import {
  AnswerChecker, QuizSession, Type, blankState, hasKanji, parseSentences, parseWords, romajiToHiragana,
} from './logic.js';

const read = (f) => readFileSync(new URL(`../wordlists/${f}`, import.meta.url), 'utf8');
const words = parseWords(read('jlpt_n5.tsv'));
const sentences = parseSentences(read('jlpt_n5_sentences.tsv'));
const checker = new AnswerChecker(words);
const byWord = (w, meaning) => words.find((x) => x.word === w && (!meaning || x.meaning.startsWith(meaning)));

test('word list is complete', () => {
  assert.equal(words.length, 660);
  assert.equal(new Set(words.map((w) => w.id)).size, words.length);
  for (const w of words) {
    assert.ok(w.word && w.meaning, w.id);
    assert.ok(w.readings.every((r) => /^[ぁ-ゖァ-ヺー]+$/.test(r)), `${w.id} reading ${w.kana}`);
  }
});

test('every word is in one of the categories', () => {
  const cats = new Set(words.map((w) => w.category));
  assert.ok(!cats.has('Other'));
  assert.ok(cats.size >= 20 && cats.size <= 30, `${cats.size} categories`);
  // The file is ordered by category, so each category is one contiguous block.
  const order = words.map((w) => w.category).filter((c, i, a) => c !== a[i - 1]);
  assert.equal(order.length, cats.size);
});

test('every word has a sentence that uses one of its forms', () => {
  const byId = new Map(words.map((w) => [w.id, w]));
  assert.equal(sentences.length, words.length);
  for (const s of sentences) {
    const w = byId.get(s.wordId);
    assert.ok(w, `unknown id ${s.wordId}`);
    assert.ok(w.forms.has(s.answer), `${s.wordId}: 【${s.answer}】 is not a form of ${w.word}`);
    assert.ok(s.english.trim(), `${s.wordId}: empty translation`);
  }
  assert.deepEqual(new Set(sentences.map((s) => s.wordId)), new Set(byId.keys()));
});

test('romaji to hiragana', () => {
  const cases = {
    taberu: 'たべる', toukyou: 'とうきょう', tōkyō: 'とうきょう', konnichiwa: 'こんにちわ',
    "kon'nichiwa": 'こんにちわ', konnnichiwa: 'こんにちわ', onna: 'おんな', shinbun: 'しんぶん',
    shimbun: 'しんぶん', kitte: 'きって', matcha: 'まっちゃ', ryokou: 'りょこう', hon: 'ほん',
    tsukue: 'つくえ', jisho: 'じしょ', zisyo: 'じしょ', fuyu: 'ふゆ', 'gen\'in': 'げんいん', ojiisan: 'おじいさん',
  };
  for (const [romaji, kana] of Object.entries(cases)) assert.equal(romajiToHiragana(romaji), kana, romaji);
  assert.equal(romajiToHiragana('qq1'), null);
});

test('written answers: kanji and kana both count', () => {
  const nomu = byWord('飲む');
  assert.equal(checker.checkWritten('飲む', nomu).kind, 'correct');
  assert.equal(checker.checkWritten('のむ', nomu).kind, 'note');
  assert.equal(checker.checkWritten('nomu', nomu).kind, 'again');
  assert.equal(checker.checkWritten('食べる', nomu).kind, 'wrong');
  assert.equal(checker.checkWritten('', nomu).kind, 'again');
  assert.equal(checker.checkWritten('こーひー', byWord('コーヒー')).kind, 'note');
  assert.equal(checker.checkWritten('コーヒー。', byWord('コーヒー')).kind, 'correct');
});

test('written answers: a different word with the same meaning gets another try', () => {
  // 青 (blue, noun) vs 青い (blue)
  assert.equal(checker.checkWritten('青', byWord('青い')).kind, 'again');
  // 暑い (hot, weather) vs 熱い (hot, to the touch): あつい is still a correct spelling.
  assert.equal(checker.checkWritten('熱い', byWord('暑い')).kind, 'again');
  assert.equal(checker.checkWritten('あつい', byWord('暑い')).kind, 'note');
});

test('readings: kana, romaji, homographs and long vowels', () => {
  const tokei = byWord('時計');
  assert.equal(checker.checkReading('とけい', tokei).kind, 'correct');
  assert.equal(checker.checkReading('tokei', tokei).kind, 'correct');
  assert.equal(checker.checkReading('トケイ', tokei).kind, 'correct');
  assert.equal(checker.checkReading('時計', tokei).kind, 'again');
  assert.equal(checker.checkReading('とき', tokei).kind, 'wrong');

  const ryokou = byWord('旅行');
  assert.equal(checker.checkReading('ryoko', ryokou).kind, 'again');
  assert.equal(checker.checkReading('ryokō', ryokou).kind, 'correct');
  assert.equal(checker.checkReading('kite', byWord('切手')).kind, 'again');

  const tsuitachi = byWord('一日', 'the 1st');
  assert.equal(checker.checkReading('ついたち', tsuitachi).kind, 'correct');
  assert.equal(checker.checkReading('いちにち', tsuitachi).kind, 'note');
  assert.ok(checker.isHomograph(tsuitachi));
  assert.ok(!checker.isHomograph(byWord('時計')));

  assert.equal(checker.checkReading('ku', byWord('九')).kind, 'correct');
  assert.equal(checker.checkReading('kyuu', byWord('九')).kind, 'correct');
});

test('quiz session: missed words come back, round passes after enough correct answers', () => {
  let seed = 1;
  const random = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  const pool = words.slice(0, 20);
  const bySentence = new Map(sentences.map((s) => [s.wordId, [s]]));
  const session = new QuizSession({ pool, sentences: bySentence, state: blankState, needed: 3, random });

  const first = session.next(0);
  session.record(first.word, false);
  const seen = [session.next(0), session.next(0), session.next(0)].map((q) => q.word.id);
  assert.equal(seen[2], first.word.id, 'missed word returns two questions later');

  for (let i = 0; i < 3; i++) session.record(session.next(0).word, true);
  assert.ok(session.passed);
});

test('reading questions only for words with kanji', () => {
  const pool = [byWord('コーヒー')];
  const session = new QuizSession({ pool, sentences: new Map(), state: blankState, needed: 1 });
  for (let i = 0; i < 30; i++) assert.notEqual(session.next(0).type, Type.READ);
  assert.ok(hasKanji('飲む') && !hasKanji('のむ'));
});

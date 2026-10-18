// Quiz logic for KotobaGate: word lists, kana/romaji, answer checking and spaced repetition.
// No DOM access here, so it runs under `node --test` as well as in the browser.

// ---------- Word lists ----------

/** Lines are `id<TAB>word<TAB>kana<TAB>meaning<TAB>also`; kana and also are `/`-separated. */
export function parseWords(tsv) {
  return lines(tsv).map((c) => {
    const word = { id: c[0], word: c[1], kana: c[2], meaning: c[3] };
    word.readings = split(c[2]);
    word.also = split(c[4] || '');
    /** Every way of writing this word that counts as a correct answer. */
    word.forms = new Set([word.word, ...word.readings, ...word.also]);
    word.meaningParts = meaningParts(word.meaning);
    return word;
  });
}

const bracket = /^(.*?)【(.+?)】(.*)$/;

/** Lines are `id<TAB>sentence with 【answer】<TAB>english`. */
export function parseSentences(tsv) {
  return lines(tsv).flatMap((c) => {
    const m = c.length >= 3 && bracket.exec(c[1]);
    if (!m) return [];
    const [, before, answer, after] = m;
    return [{
      wordId: c[0], before, answer, after, english: c[2],
      withBlank: `${before}＿＿${after}`, full: before + answer + after,
    }];
  });
}

function lines(tsv) {
  return tsv.split('\n').slice(1).filter((l) => l.trim()).map((l) => l.replace(/\r$/, '').split('\t'));
}

function split(s) {
  return s.split('/').map((x) => x.trim()).filter(Boolean);
}

/** "hot (weather); warm" → ["hot", "warm"]: the parts that make two glosses "the same". */
function meaningParts(meaning) {
  return meaning.split(';')
    .map((p) => p.replace(/\(.*?\)/g, '').trim().toLowerCase().replace(/^to /, ''))
    .filter(Boolean);
}

// ---------- Kana ----------

export const hasKanji = (s) => /[㐀-鿿々]/.test(s);
const isKana = (s) => /^[ぁ-ゖァ-ヺー]+$/.test(s);
const isLatin = (s) => /^[a-zA-Z0-9'\-āīūēōâîûêôĀĪŪĒŌ]+$/.test(s);
export const isKatakana = (s) => /^[ァ-ヺー]+$/.test(s);

export function toHiragana(s) {
  return s.replace(/[ァ-ヶ]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0x60));
}

const ROMAJI = {
  a: 'あ', i: 'い', u: 'う', e: 'え', o: 'お',
  ka: 'か', ki: 'き', ku: 'く', ke: 'け', ko: 'こ', kya: 'きゃ', kyu: 'きゅ', kyo: 'きょ',
  ga: 'が', gi: 'ぎ', gu: 'ぐ', ge: 'げ', go: 'ご', gya: 'ぎゃ', gyu: 'ぎゅ', gyo: 'ぎょ',
  sa: 'さ', si: 'し', shi: 'し', su: 'す', se: 'せ', so: 'そ',
  sha: 'しゃ', shu: 'しゅ', sho: 'しょ', she: 'しぇ', sya: 'しゃ', syu: 'しゅ', syo: 'しょ',
  za: 'ざ', zi: 'じ', ji: 'じ', zu: 'ず', ze: 'ぜ', zo: 'ぞ',
  ja: 'じゃ', ju: 'じゅ', jo: 'じょ', je: 'じぇ', zya: 'じゃ', zyu: 'じゅ', zyo: 'じょ', jya: 'じゃ', jyu: 'じゅ', jyo: 'じょ',
  ta: 'た', ti: 'ち', chi: 'ち', tu: 'つ', tsu: 'つ', te: 'て', to: 'と',
  cha: 'ちゃ', chu: 'ちゅ', cho: 'ちょ', che: 'ちぇ', tya: 'ちゃ', tyu: 'ちゅ', tyo: 'ちょ',
  da: 'だ', di: 'ぢ', du: 'づ', de: 'で', do: 'ど', dya: 'ぢゃ', dyu: 'ぢゅ', dyo: 'ぢょ',
  na: 'な', ni: 'に', nu: 'ぬ', ne: 'ね', no: 'の', nya: 'にゃ', nyu: 'にゅ', nyo: 'にょ',
  ha: 'は', hi: 'ひ', hu: 'ふ', fu: 'ふ', he: 'へ', ho: 'ほ', hya: 'ひゃ', hyu: 'ひゅ', hyo: 'ひょ',
  fa: 'ふぁ', fi: 'ふぃ', fe: 'ふぇ', fo: 'ふぉ',
  ba: 'ば', bi: 'び', bu: 'ぶ', be: 'べ', bo: 'ぼ', bya: 'びゃ', byu: 'びゅ', byo: 'びょ',
  pa: 'ぱ', pi: 'ぴ', pu: 'ぷ', pe: 'ぺ', po: 'ぽ', pya: 'ぴゃ', pyu: 'ぴゅ', pyo: 'ぴょ',
  ma: 'ま', mi: 'み', mu: 'む', me: 'め', mo: 'も', mya: 'みゃ', myu: 'みゅ', myo: 'みょ',
  ya: 'や', yu: 'ゆ', yo: 'よ',
  ra: 'ら', ri: 'り', ru: 'る', re: 'れ', ro: 'ろ', rya: 'りゃ', ryu: 'りゅ', ryo: 'りょ',
  la: 'ら', li: 'り', lu: 'る', le: 'れ', lo: 'ろ',
  wa: 'わ', wo: 'を', vu: 'ゔ',
  xa: 'ぁ', xi: 'ぃ', xu: 'ぅ', xe: 'ぇ', xo: 'ぉ', xya: 'ゃ', xyu: 'ゅ', xyo: 'ょ', xtu: 'っ', xtsu: 'っ',
  '-': 'ー',
};
const MACRONS = { ā: 'aa', ī: 'ii', ū: 'uu', ē: 'ee', ō: 'ou', â: 'aa', î: 'ii', û: 'uu', ê: 'ee', ô: 'ou' };
const VOWELS = 'aiueo';

/**
 * Hepburn, kunrei or keyboard-style romaji to hiragana. "konnichiwa", "kon'nichiwa" and
 * "konnnichiwa" all work; ō becomes おう (callers treat long vowels loosely).
 * Returns null if something can't be read.
 */
export function romajiToHiragana(input) {
  const s = input.toLowerCase().normalize('NFC').replace(/[āīūēōâîûêô]/g, (c) => MACRONS[c]);
  let out = '';
  let i = 0;
  while (i < s.length) {
    const c = s[i];
    if (c === "'" || c === ' ') { i++; continue; }
    if (c === 'n') {
      const next = s[i + 1];
      if (next === 'n') {
        // "nn" + vowel/y is ん + na… (Hepburn "onna"); otherwise it's the keyboard spelling of ん.
        out += 'ん';
        i += VOWELS.includes(s[i + 2]) || s[i + 2] === 'y' ? 1 : 2;
        continue;
      }
      if (next === undefined || next === "'" || !(VOWELS.includes(next) || next === 'y')) {
        out += 'ん'; i++; continue;
      }
    }
    // Hepburn writes ん as m before b/p: "shimbun", "sampo".
    if (c === 'm' && (s[i + 1] === 'b' || s[i + 1] === 'p')) { out += 'ん'; i++; continue; }
    if (c === s[i + 1] && /[bcdfghjklmpqrstvwxyz]/.test(c)) { out += 'っ'; i++; continue; }
    if (c === 't' && s[i + 1] === 'c' && s[i + 2] === 'h') { out += 'っ'; i++; continue; } // "matcha"
    let matched = false;
    for (const len of [4, 3, 2, 1]) {
      const kana = ROMAJI[s.slice(i, i + len)];
      if (kana) { out += kana; i += len; matched = true; break; }
    }
    if (!matched) return null;
  }
  return out;
}

const ROWS = {
  a: 'あかさたなはまやらわがざだばぱぁゃゎ',
  i: 'いきしちにひみりぎじぢびぴぃ',
  u: 'うくすつぬふむゆるぐずづぶぷぅゅゔ',
  e: 'えけせてねへめれげぜでべぺぇ',
  o: 'おこそとのほもよろをごぞどぼぽぉょ',
};
const vowelOf = (ch) => Object.keys(ROWS).find((v) => ROWS[v].includes(ch));
const LONG = { a: 'あ', i: 'い', u: 'う', e: 'いえ', o: 'うお' };

/** Hiragana with ぢ/づ folded into じ/ず (romaji can't tell them apart). */
function fold(s) {
  return toHiragana(s).replace(/ぢ/g, 'じ').replace(/づ/g, 'ず');
}

/** Ignores vowel length and small っ, for "almost right" hints: とうきょう ≈ ときょ. */
function loose(s) {
  let out = '';
  let prev = '';
  for (const ch of fold(s)) {
    if (ch === 'っ' || ch === 'ー') continue;
    const v = vowelOf(prev);
    if (v && LONG[v].includes(ch)) continue;
    out += ch;
    prev = ch;
  }
  return out;
}

// ---------- Answer checking ----------

/** kind: 'correct' | 'note' (correct, with a remark) | 'again' (not counted, try again) | 'wrong'. */
const correct = { kind: 'correct' };
const note = (text) => ({ kind: 'note', text });
const again = (text) => ({ kind: 'again', text });
const wrong = { kind: 'wrong' };

export class AnswerChecker {
  constructor(words) {
    this.byForm = new Map();
    for (const w of words) {
      for (const f of w.forms) {
        for (const key of new Set([f, toHiragana(f)])) {
          if (!this.byForm.has(key)) this.byForm.set(key, []);
          this.byForm.get(key).push(w);
        }
      }
    }
  }

  static clean(input) {
    return input.normalize('NFC').replace(/[\s。、，．,.!！?？「」『』"'“”～〜・]/g, '');
  }

  /** English → Japanese, and fill-in-the-blank: kanji or kana both count. */
  checkWritten(input, target) {
    const a = AnswerChecker.clean(input);
    if (!a) return again('Type an answer first.');
    if (isLatin(a)) return again('Switch to the Japanese keyboard (🌐) and type in kana or kanji.');
    if (target.forms.has(a)) {
      return isKana(a) && hasKanji(target.word) ? note(`Usually written ${target.word}.`) : correct;
    }
    if (isKana(a) && [...target.forms].some((f) => isKana(f) && toHiragana(f) === toHiragana(a))) {
      return note(`Usually written ${target.word}.`);
    }
    const others = (this.byForm.get(a) || this.byForm.get(toHiragana(a)) || []).filter((o) => o !== target);
    // A different word with exactly the same gloss: fully correct.
    const same = others.find((o) => o.meaning === target.meaning);
    if (same) return note(`Also right! Expected ${target.word}.`);
    const overlap = others.find((o) => o.meaningParts.some((p) => target.meaningParts.includes(p)));
    if (overlap) {
      return again(`${overlap.word} means "${overlap.meaning}", but I'm thinking of a different word.`);
    }
    return wrong;
  }

  /** Kanji → reading, typed in kana or romaji. */
  checkReading(input, target) {
    const raw = AnswerChecker.clean(input);
    if (!raw) return again('Type the reading first.');
    const usedMacron = /[āīūēōâîûêô]/i.test(raw);
    const kana = isLatin(raw) ? romajiToHiragana(raw) : raw;
    if (!kana || !isKana(kana)) return again('Type the reading in kana (or romaji), not kanji.');
    const got = fold(kana);
    if (target.readings.some((r) => fold(r) === got)) return correct;
    // Another word written the same way (家: いえ / うち).
    const homograph = (this.byForm.get(target.word) || [])
      .find((o) => o !== target && o.readings.some((r) => fold(r) === got));
    if (homograph) return note(`Also a reading of ${target.word} (${homograph.meaning}). Here: ${target.readings[0]}.`);
    const close = target.readings.find((r) => loose(r) === loose(got));
    if (close) {
      if (usedMacron) return note(`In kana: ${close}.`);
      return again('Close! Check long vowels (おう = ou) and small っ (double letter).');
    }
    return wrong;
  }

  /** The word's written form is shared with another entry, so a reading question needs a hint. */
  isHomograph(target) {
    return (this.byForm.get(target.word) || []).some((o) => o !== target);
  }
}

// ---------- Spaced repetition ----------

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
/** Wait before a word comes back after reaching box n. */
const WAITS = [0, HOUR, DAY, 3 * DAY, 7 * DAY, 21 * DAY];
export const MAX_BOX = 5;

export const blankState = () => ({ known: false, sorted: false, box: 0, due: 0 });

export const Srs = {
  correct(s, now) {
    const box = Math.min(s.box + 1, MAX_BOX);
    return { ...s, box, due: now + WAITS[box] };
  },
  wrong(s, now) {
    return { ...s, box: 0, due: now };
  },
};

// ---------- Quiz session ----------

export const Type = { WRITE: 'write', READ: 'read', SENTENCE: 'sentence' };

/**
 * One round of questions. Words answered wrong come back two questions later,
 * and the round is passed after `needed` correct answers.
 */
export class QuizSession {
  constructor({ pool, sentences, state, needed, random = Math.random }) {
    Object.assign(this, { pool, sentences, state, needed, random });
    this.correctCount = 0;
    this.retry = []; // [word, questions to wait]
    this.recent = [];
  }

  get passed() { return this.correctCount >= this.needed; }

  next(now) {
    const word = this.takeRetry() ?? this.pickWord(now);
    this.recent.push(word.id);
    while (this.recent.length > Math.max(0, Math.min(5, this.pool.length - 1))) this.recent.shift();
    const type = this.pickType(word);
    const sentence = type === Type.SENTENCE ? this.pick(this.sentences.get(word.id)) : null;
    return { type, word, sentence };
  }

  record(word, ok) {
    if (ok) this.correctCount++;
    else this.retry.push([word, 2]);
  }

  takeRetry() {
    const i = this.retry.findIndex(([, wait]) => wait <= 0);
    const ready = i >= 0 ? this.retry.splice(i, 1)[0][0] : null;
    for (const r of this.retry) r[1]--;
    return ready;
  }

  pickWord(now) {
    let fresh = this.pool.filter((w) => !this.recent.includes(w.id));
    if (!fresh.length) fresh = this.pool;
    const due = fresh.filter((w) => this.state(w.id).due <= now);
    if (due.length) {
      // Lowest box first, with a little shuffling so it isn't always the same order.
      const lowest = Math.min(...due.map((w) => this.state(w.id).box));
      return this.pick(due.filter((w) => this.state(w.id).box <= lowest + 1));
    }
    // Nothing due: favour weaker words.
    const weights = fresh.map((w) => 1 / (this.state(w.id).box + 1));
    let r = this.random() * weights.reduce((a, b) => a + b, 0);
    for (let i = 0; i < fresh.length; i++) {
      r -= weights[i];
      if (r <= 0) return fresh[i];
    }
    return fresh[fresh.length - 1];
  }

  pickType(word) {
    const types = [Type.WRITE, Type.WRITE];
    if (hasKanji(word.word)) types.push(Type.READ);
    if (this.sentences.get(word.id)?.length) types.push(Type.SENTENCE, Type.SENTENCE);
    return this.pick(types);
  }

  pick(list) {
    return list[Math.floor(this.random() * list.length)];
  }
}

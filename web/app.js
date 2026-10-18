import {
  AnswerChecker, QuizSession, Srs, Type, blankState, isKatakana,
  parseSentences, parseWords, romajiToHiragana, toHiragana,
} from './logic.js';

const BLOCKED_APP = 'Instagram';
/** Until this many words are marked as known, the quiz uses the whole list. */
const MIN_KNOWN = 5;
const SKIP_MINUTES = 5;
const SKIP_WAIT_SECONDS = 30;

// ---------- Saved progress and settings (this browser only) ----------

const KEY = 'kotobagate';
const store = {
  data: { states: {}, needed: 3, unlockMinutes: 15, shortcut: 'KotobaGate Unlock', unlockedUntil: 0 },

  load() {
    try {
      const saved = JSON.parse(localStorage.getItem(KEY) || '{}');
      Object.assign(this.data, saved);
    } catch { /* private mode or blocked storage: start fresh */ }
  },
  save() {
    try { localStorage.setItem(KEY, JSON.stringify(this.data)); } catch { /* ignore */ }
  },
  /** States are stored as [known, sorted, box, due] to keep the saved data small. */
  state(id) {
    const a = this.data.states[id];
    return a ? { known: !!a[0], sorted: !!a[1], box: a[2], due: a[3] } : blankState();
  },
  update(id, s) {
    this.data.states[id] = [s.known ? 1 : 0, s.sorted ? 1 : 0, s.box, s.due];
    this.save();
  },
  set(key, value) {
    this.data[key] = value;
    this.save();
  },
};

// ---------- Data ----------

let words = [];
let sentences = new Map();
let checker;

async function loadData() {
  const [w, s] = await Promise.all(
    ['jlpt_n5.tsv', 'jlpt_n5_sentences.tsv'].map((f) => fetch(f).then((r) => {
      if (!r.ok) throw new Error(`${f}: ${r.status}`);
      return r.text();
    })),
  );
  words = parseWords(w);
  for (const x of parseSentences(s)) {
    if (!sentences.has(x.wordId)) sentences.set(x.wordId, []);
    sentences.get(x.wordId).push(x);
  }
  checker = new AnswerChecker(words);
}

/** Words the quiz draws from: the ones marked as known, or everything until enough are marked. */
function quizPool() {
  const known = words.filter((w) => store.state(w.id).known);
  return known.length >= MIN_KNOWN ? known : words;
}

const newSession = (needed) =>
  new QuizSession({ pool: quizPool(), sentences, state: (id) => store.state(id), needed });

// ---------- Tiny DOM helper ----------

function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'class') el.className = v;
    else if (k in el) el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

const app = document.getElementById('app');
function show(...nodes) {
  app.replaceChildren(...nodes);
  window.scrollTo(0, 0);
}

const timeOf = (ms) => new Date(ms).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });

// ---------- Speech ----------

function say(text) {
  if (!('speechSynthesis' in window)) return;
  const u = new SpeechSynthesisUtterance(text);
  u.lang = 'ja-JP';
  const voice = speechSynthesis.getVoices().find((v) => v.lang.replace('_', '-').startsWith('ja'));
  if (voice) u.voice = voice;
  u.rate = 0.9;
  speechSynthesis.cancel();
  speechSynthesis.speak(u);
}

// ---------- Quiz ----------

/**
 * Asks questions from `session` until it is passed, then shows `finishLabel` which calls `onFinished`.
 * `extra` is drawn under the question (used for the emergency skip on the gate).
 */
function quizScreen({ session, title, finishLabel, onFinished, extra }) {
  let question = session.next(Date.now());
  let outcome = null; // { right, note }
  let hint = null;
  let input;

  function settle(ok, note = null) {
    const now = Date.now();
    const w = question.word;
    session.record(w, ok);
    const s = store.state(w.id);
    store.update(w.id, ok ? Srs.correct(s, now) : Srs.wrong(s, now));
    outcome = { right: ok, note };
    hint = null;
    say(question.sentence?.full ?? w.readings[0]);
    render();
  }

  function submit() {
    if (outcome) return;
    const q = question;
    const v = q.type === Type.READ ? checker.checkReading(input.value, q.word) : checker.checkWritten(input.value, q.word);
    if (v.kind === 'correct') settle(true);
    else if (v.kind === 'note') settle(true, v.text);
    else if (v.kind === 'wrong') settle(false);
    else { hint = v.text; render(); input.focus(); }
  }

  function next() {
    question = session.next(Date.now());
    outcome = null;
    input.value = '';
    render();
    input.focus(); // still inside the tap, so iOS opens the keyboard
  }

  function render() {
    const typed = input?.value ?? '';
    const progress = Math.min(session.correctCount, session.needed);
    const nodes = [
      h('p', { class: 'eyebrow' }, title),
      h('div', { class: 'progress' }, h('div', { style: `width:${(progress / session.needed) * 100}%` })),
      h('p', { class: 'muted small' }, `${progress} / ${session.needed} correct`),
      prompt(question),
    ];
    if (!outcome) {
      const hintEl = h('p', { class: 'hint' }, hint ?? '');
      input = h('input', {
        class: 'answer', type: 'text', lang: 'ja', value: typed,
        autocomplete: 'off', autocapitalize: 'off', spellcheck: false, enterKeyHint: 'done',
        placeholder: question.type === Type.READ ? 'かな or romaji' : '日本語',
        onInput: () => { hint = null; hintEl.textContent = ''; },
        onKeydown: (e) => {
          // Enter during kana→kanji conversion only confirms the conversion.
          if (e.key === 'Enter' && !e.isComposing && e.keyCode !== 229) { e.preventDefault(); submit(); }
        },
      });
      input.setAttribute('autocorrect', 'off');
      nodes.push(
        input, hintEl,
        h('div', { class: 'row' },
          h('button', { class: 'primary', onClick: submit }, 'Check'),
          h('button', { onClick: () => settle(false) }, "I don't know")),
      );
    } else {
      nodes.push(reveal(question, outcome));
      nodes.push(session.passed
        ? h('button', { class: 'primary wide', onClick: onFinished }, finishLabel)
        : h('button', { class: 'primary wide', onClick: next }, 'Next'));
    }
    if (extra) nodes.push(extra);
    show(h('main', { class: 'quiz' }, nodes));
    if (!outcome && document.activeElement !== input) input.focus();
  }

  render();
}

function prompt(q) {
  const w = q.word;
  let label, big, small;
  if (q.type === Type.WRITE) {
    label = 'Write in Japanese';
    big = w.meaning;
    const n = w.readings[0].length;
    small = isKatakana(w.word) ? `katakana · ${n} kana` : `${n} kana`;
  } else if (q.type === Type.READ) {
    label = 'Type the reading (kana or romaji)';
    big = w.word;
    small = checker.isHomograph(w) ? w.meaning : null;
  } else {
    label = 'Fill in the blank';
    big = q.sentence.withBlank;
    small = q.sentence.english;
  }
  return h('section', { class: 'prompt' },
    h('p', { class: 'label' }, label),
    h('p', { class: `big ${q.type}`, lang: q.type === Type.WRITE ? 'en' : 'ja' }, big),
    small && h('p', { class: 'muted' }, small));
}

function reveal(q, outcome) {
  const w = q.word;
  return h('section', { class: `card reveal ${outcome.right ? 'right' : 'missed'}` },
    h('p', { class: 'verdict' }, outcome.right ? '✓ Correct' : '✗ Not quite'),
    outcome.note && h('p', {}, outcome.note),
    h('div', { class: 'word-line' },
      h('span', { class: 'jp', lang: 'ja' }, w.word),
      h('div', {},
        w.word !== w.kana && h('p', { class: 'reading', lang: 'ja' }, w.readings.join('・')),
        h('p', { class: 'muted' }, w.meaning))),
    q.sentence && h('p', { class: 'sentence', lang: 'ja' }, q.sentence.full),
    q.sentence && h('p', { class: 'muted small' }, q.sentence.english),
    'speechSynthesis' in window &&
      h('button', { class: 'link', onClick: () => say(q.sentence?.full ?? w.readings[0]) }, '🔊 Listen'),
    !outcome.right && h('p', { class: 'muted small' }, 'This word will come back in a moment.'));
}

// ---------- The gate in front of Instagram ----------

let gateSession = null;

function gateScreen() {
  if (!gateSession || gateSession.passed) gateSession = newSession(store.data.needed);
  quizScreen({
    session: gateSession,
    title: `Answer ${gateSession.needed} to open ${BLOCKED_APP}`,
    finishLabel: `Open ${BLOCKED_APP}`,
    onFinished: () => unlock(store.data.unlockMinutes),
    extra: emergencySkip(),
  });
}

/** Runs the Shortcuts shortcut that saves the unlock time and opens Instagram. */
function unlock(minutes) {
  store.set('unlockedUntil', Date.now() + minutes * 60_000);
  gateSession = null;
  const name = encodeURIComponent(store.data.shortcut);
  location.href = `shortcuts://run-shortcut?name=${name}&input=text&text=${minutes}`;
  setTimeout(() => { location.hash = ''; }, 1500);
}

/** A way out in case something breaks, slow enough that it isn't tempting. */
function emergencySkip() {
  const wrap = h('div', { class: 'skip' });
  let timer = null;
  const idle = () => wrap.replaceChildren(
    h('button', { class: 'link muted', onClick: start }, `Emergency skip (${SKIP_MINUTES} min)`));
  function start() {
    let left = SKIP_WAIT_SECONDS;
    const btn = h('button', { class: 'link muted', onClick: () => { clearInterval(timer); idle(); } });
    const tick = () => { btn.textContent = `Skipping in ${left}s… tap to cancel`; };
    tick();
    wrap.replaceChildren(btn);
    timer = setInterval(() => {
      left--;
      if (left > 0) return tick();
      clearInterval(timer);
      unlock(SKIP_MINUTES);
    }, 1000);
  }
  window.addEventListener('hashchange', () => clearInterval(timer), { once: true });
  idle();
  return wrap;
}

// ---------- Home ----------

function homeScreen() {
  const d = store.data;
  const known = words.filter((w) => store.state(w.id).known).length;
  const unsorted = words.filter((w) => !store.state(w.id).sorted).length;
  const unlocked = d.unlockedUntil > Date.now();

  show(h('main', {},
    h('h1', {}, 'KotobaGate ', h('span', { lang: 'ja' }, 'ことば門')),
    h('p', { class: 'muted' }, `A short Japanese quiz in front of ${BLOCKED_APP}. JLPT N5 vocabulary.`),

    h('section', { class: 'card' },
      h('h2', {}, unlocked ? `${BLOCKED_APP} is open until ${timeOf(d.unlockedUntil)}` : `${BLOCKED_APP} gate`),
      h('p', { class: 'muted small' }, 'The gate runs through the Shortcuts app. Set it up once on the iPhone.'),
      h('a', { class: 'button', href: '#setup' }, 'Setup guide')),

    h('section', { class: 'card' },
      h('h2', {}, `Words: ${known} of ${words.length} known`),
      known < MIN_KNOWN && h('p', { class: 'muted small' },
        `Until you mark at least ${MIN_KNOWN} words, the quiz uses all N5 words.`),
      h('a', { class: `button primary${unsorted ? '' : ' disabled'}`, href: unsorted ? '#sort' : null },
        unsorted ? `Sort words (${unsorted} left)` : 'All words sorted'),
      h('a', { class: 'button', href: '#list' }, 'Word list & search'),
      h('a', { class: 'button', href: '#practice' }, 'Practice now')),

    h('section', { class: 'card' },
      h('h2', {}, 'Settings'),
      stepper(`Correct answers to open ${BLOCKED_APP}`, 'needed', 1, 10, 1),
      stepper(`${BLOCKED_APP} stays open for (minutes)`, 'unlockMinutes', 5, 120, 5),
      backup()),
  ));
}

function stepper(label, key, min, max, step) {
  const value = h('b', { class: 'value' }, store.data[key]);
  const change = (delta) => {
    store.set(key, Math.min(max, Math.max(min, store.data[key] + delta)));
    value.textContent = store.data[key];
  };
  return h('div', { class: 'stepper' },
    h('span', {}, label),
    h('button', { 'aria-label': 'less', onClick: () => change(-step) }, '−'),
    value,
    h('button', { 'aria-label': 'more', onClick: () => change(step) }, '+'));
}

/** Progress lives in Safari's storage; this lets it be copied somewhere safe and restored. */
function backup() {
  const box = h('textarea', { rows: 3, placeholder: 'Backup text appears here, or paste one to restore.' });
  const msg = h('p', { class: 'muted small' });
  return h('details', {},
    h('summary', {}, 'Backup & restore'),
    h('p', { class: 'muted small' },
      'Progress is saved in Safari on this phone only. Copy the backup into Notes now and then.'),
    box,
    h('div', { class: 'row' },
      h('button', {
        onClick: async () => {
          box.value = JSON.stringify(store.data);
          try { await navigator.clipboard.writeText(box.value); msg.textContent = 'Copied.'; } catch { box.select(); }
        },
      }, 'Make backup'),
      h('button', {
        onClick: () => {
          try {
            const data = JSON.parse(box.value);
            if (typeof data.states !== 'object') throw new Error();
            store.data = { ...store.data, ...data };
            store.save();
            msg.textContent = 'Restored.';
          } catch { msg.textContent = "That doesn't look like a KotobaGate backup."; }
        },
      }, 'Restore')),
    msg);
}

// ---------- Sorting words ----------

function sortScreen() {
  const history = [];

  function render() {
    const word = words.find((w) => !store.state(w.id).sorted);
    const sortedCount = words.filter((w) => store.state(w.id).sorted).length;
    const mark = (known) => {
      store.update(word.id, { ...store.state(word.id), sorted: true, known, due: 0 });
      history.push(word.id);
      render();
    };
    show(h('main', { class: 'center' },
      h('p', { class: 'eyebrow' }, 'Do you know this word?'),
      h('p', { class: 'muted small' }, `${sortedCount} / ${words.length}`),
      word
        ? [
          h('p', { class: 'jp huge', lang: 'ja' }, word.word),
          word.word !== word.kana && h('p', { class: 'reading', lang: 'ja' }, word.readings.join('・')),
          h('p', {}, word.meaning),
          h('div', { class: 'row even' },
            h('button', { class: 'tall', onClick: () => mark(false) }, 'Not yet'),
            h('button', { class: 'tall primary', onClick: () => mark(true) }, 'Know it')),
        ]
        : [h('h2', {}, 'All words sorted!'), h('a', { class: 'button primary', href: '#' }, 'Back')],
      history.length > 0 && h('button', {
        class: 'link',
        onClick: () => {
          const id = history.pop();
          store.update(id, { ...store.state(id), sorted: false, known: false });
          render();
        },
      }, 'Undo'),
      h('a', { class: 'link', href: '#' }, 'Done for now')));
  }
  render();
}

// ---------- Word list ----------

function listScreen() {
  let query = '';
  let filter = 'all';
  const count = h('p', { class: 'muted small' });
  const list = h('ul', { class: 'words' });
  const chips = h('div', { class: 'row' });

  function matches(w) {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    if ([...w.forms].some((f) => f.includes(q))) return true;
    const escaped = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    if (new RegExp(`\\b${escaped}`).test(w.meaning.toLowerCase())) return true;
    const kana = /^[a-z' -]+$/.test(q) ? romajiToHiragana(q) : toHiragana(q);
    return !!kana && w.readings.some((r) => toHiragana(r).includes(kana));
  }

  function render() {
    chips.replaceChildren(...[['all', 'All'], ['known', 'Known'], ['new', 'Not yet']].map(([f, label]) =>
      h('button', { class: `chip${filter === f ? ' on' : ''}`, onClick: () => { filter = f; render(); } }, label)));
    const shown = words.filter((w) => {
      const known = store.state(w.id).known;
      return (filter === 'all' || (filter === 'known') === known) && matches(w);
    });
    count.textContent = `${shown.length} words`;
    list.replaceChildren(...shown.map((w) => {
      const s = store.state(w.id);
      const box = h('input', {
        type: 'checkbox', checked: s.known, 'aria-label': 'known',
        onChange: () => store.update(w.id, { ...store.state(w.id), known: box.checked, sorted: true }),
      });
      return h('li', {},
        h('label', {},
          h('span', { class: 'jp', lang: 'ja' }, w.word),
          h('span', { class: 'gloss' },
            w.word !== w.kana && h('span', { class: 'reading', lang: 'ja' }, w.readings.join('・')),
            h('span', { class: 'muted' }, w.meaning)),
          box));
    }));
  }

  show(h('main', {},
    h('a', { class: 'link', href: '#' }, '← Back'),
    h('input', {
      class: 'search', type: 'search', placeholder: 'Search: 日本語, kana, romaji or English',
      autocapitalize: 'off', onInput: (e) => { query = e.target.value; render(); },
    }),
    chips, count, list));
  render();
}

// ---------- Setup guide ----------

function setupScreen() {
  const gateUrl = `${location.origin}${location.pathname}#gate`;
  const d = store.data;
  const copy = (text) => h('button', {
    class: 'small', onClick: async (e) => {
      try { await navigator.clipboard.writeText(text); e.target.textContent = 'Copied'; } catch { /* ignore */ }
    },
  }, 'Copy');

  const step = (title, ...items) => h('section', { class: 'card' }, h('h2', {}, title), h('ol', {}, items.map((i) => h('li', {}, i))));

  show(h('main', { class: 'guide' },
    h('a', { class: 'link', href: '#' }, '← Back'),
    h('h1', {}, 'Set up the gate'),
    h('p', { class: 'muted' },
      `iPhones don't let apps block other apps, so the Shortcuts app does the gating: when ${BLOCKED_APP} opens, `
      + 'a shortcut checks whether it was unlocked recently and, if not, opens this quiz. Passing the quiz runs a '
      + `second shortcut that saves the unlock time and opens ${BLOCKED_APP}. One-time setup, about 10 minutes.`),
    h('p', { class: 'muted small' },
      'Use this page in Safari (not a home-screen web app), because progress is stored in Safari. '
      + 'A bookmark or "Add to Home Screen" from Safari is fine.'),

    step('1. Japanese keyboard',
      'Settings → General → Keyboard → Keyboards → Add New Keyboard → Japanese.',
      'Pick Kana (flick) or Romaji (QWERTY). Switch keyboards with 🌐 while answering.'),

    step(`2. Shortcut "${d.shortcut}"`,
      'Open Shortcuts → + (new shortcut). Name it exactly: ', h('code', {}, d.shortcut), ' ', copy(d.shortcut),
      'Add action Date (it gives Current Date).',
      'Add action Adjust Date: Add  [Shortcut Input]  minutes  to  [Date]. (Tap the number field and pick Shortcut Input.)',
      'Add action Format Date: [Adjusted Date], Date Format: ISO 8601, Include Time: on.',
      'Add action Save File: [Formatted Date]. Turn off "Ask Where to Save", set the folder to Shortcuts, '
        + 'Subpath ', h('code', {}, 'KotobaGate/unlocked.txt'), ', and turn on Overwrite If File Exists.',
      `Add action Open App: ${BLOCKED_APP}.`),

    step('3. Shortcut "KotobaGate Check"',
      'New shortcut, named KotobaGate Check.',
      'Add action Get File from Folder: folder Shortcuts, path ', h('code', {}, 'KotobaGate/unlocked.txt'),
        ', turn off "Error If Not Found".',
      'Add action Get Dates from Input: [File].',
      'Add action If: [Current Date] is before [Dates]. (Tap Input → Current Date; the If needs a date to compare.)',
      'Leave the If part empty. In Otherwise, add Open URLs with the quiz address:',
      h('code', { class: 'block' }, gateUrl), copy(gateUrl)),

    step(`4. Automation: when ${BLOCKED_APP} opens`,
      'Shortcuts → Automation tab → + → App.',
      `Choose ${BLOCKED_APP}, tick "Is Opened", and select "Run Immediately".`,
      'Next → choose the KotobaGate Check shortcut.',
      'If there is a "Notify When Run" switch, turn it off.'),

    step('5. Try it',
      `Open ${BLOCKED_APP}. Safari should switch to the quiz.`,
      `Pass the quiz and tap "Open ${BLOCKED_APP}". Safari asks to open Shortcuts: tap Open. `
        + `${BLOCKED_APP} opens and stays open for the unlock time.`,
      'Or test step 2 alone here: ', h('button', { class: 'small', onClick: () => unlock(1) }, 'Unlock for 1 minute')),

    h('section', { class: 'card' },
      h('h2', {}, 'If something goes wrong'),
      h('ul', {},
        h('li', {}, 'The quiz opens even right after unlocking: run KotobaGate Check by hand and see which step fails. '
          + 'Usually the file path in step 2 and step 3 doesn\'t match.'),
        h('li', {}, 'Safari keeps opening new tabs: in Settings → Apps → Safari → Close Tabs, choose After One Day.'),
        h('li', {}, 'To pause the gate, open the automation and turn it off.'))),
  ));
}

// ---------- Routing ----------

function route() {
  const page = location.hash.slice(1);
  if (page === 'gate') return gateScreen();
  if (page === 'sort') return sortScreen();
  if (page === 'list') return listScreen();
  if (page === 'setup') return setupScreen();
  if (page === 'practice') {
    return quizScreen({
      session: newSession(10), title: 'Practice', finishLabel: 'Done',
      onFinished: () => { location.hash = ''; },
    });
  }
  return homeScreen();
}

store.load();
loadData().then(() => {
  window.addEventListener('hashchange', route);
  route();
}).catch((e) => {
  show(h('main', {}, h('h1', {}, 'Could not load the word list'), h('p', { class: 'muted' }, String(e))));
});

// Some browsers load voices late; ask early so the first "Listen" has a Japanese voice.
if ('speechSynthesis' in window) speechSynthesis.getVoices();

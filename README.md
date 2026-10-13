# HanziGate 汉字门

An Android app that puts a short Chinese vocabulary quiz in front of X (Twitter).
Answer a few questions correctly and X opens for a while (default: 3 answers, 15 minutes).

Vocabulary: the 500 words of New HSK (2021) Level 1, see [`wordlists/`](wordlists/).

## Question types

- **English → Chinese**: see "to drink", type 喝 (use the Chinese Pinyin keyboard).
- **Chinese → pinyin**: see 喝, type `he1` (or `hē`).
- **Fill in the blank**: 我想＿＿一杯茶。(I want to drink a cup of tea.) → 喝

Missed words come back a couple of questions later and show up more often afterwards (simple spaced repetition).
The quiz only uses words you've marked as known; sort them once under **Sort words**, and add new ones from **Word list & search**.

## Install on your phone

1. Open the repo's **Releases** page on your phone, find **HanziGate (latest from …)**, and download `HanziGate.apk`.
   (Each push rebuilds it. The APK is also attached to every run on the **Actions** tab.)
2. Open the file and allow installing from your browser/files app when asked. Play Protect may warn, since the app isn't from the store; choose install anyway.
3. Open HanziGate → **Accessibility** → Downloaded apps → HanziGate → turn on.
   On Android 13+ it may be greyed out the first time: go back, tap **App info** → ⋮ (top right) → **Allow restricted settings**, then turn it on.
4. Optional: in App info → Battery, choose **Unrestricted** so the phone doesn't stop the service.
5. For typing characters, add **Chinese (Simplified) Pinyin** in Gboard (Settings → Languages). The quiz switches keyboards for you.

If anything breaks, the quiz screen has an **Emergency skip** (30-second wait, then 5 minutes of X).

## Development

Kotlin + Jetpack Compose, built by GitHub Actions (`.github/workflows/build.yml`).
Logic and word-list checks: `./gradlew testDebugUnitTest`.
The signing key in `app/hanzigate.keystore` is deliberately public, so every build installs over the last one without losing progress.

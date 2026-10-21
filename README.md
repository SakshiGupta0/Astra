# ASTRA

Vedic astrology and tarot companion for India. *Tell your story once.*
Native Android (Kotlin + Jetpack Compose), Firebase (free Spark plan), AI server on Cloudflare Workers (free plan), Gemini. Solo build. Spec: ASTRA PRD v1.3.

## Modules
- `core/` — pure Kotlin, no Android code. All calculations: sidereal positions (Lahiri), Lagna, whole-sign houses,
  nakshatra and pada, Vimshottari dasha, birth-time states, Graha Tracker (sign changes, retrogrades, period tone,
  Saturn phases, events, chart at any past month), daily panchang, My Story rules (consent, selection, check-ins, export),
  notification planning, and tarot (deck astra-deck-1, secure draws, spreads, daily card).
  Vendored [Astronomy Engine](https://github.com/cosinekitty/astronomy) (MIT) with one small addition (`astraNutation`).
- `app/` — Compose app: Today, Tracker (Now · Timeline · Chart), Tarot, Ask, Me.
- `functions/lib/` — AI gateway logic shared by the server and the eval: prompts, caps, story selection, output checks.
- `worker/` — the AI server on Cloudflare Workers: `/ask`, `/events`, `/feedback`, `/delete-me`, `/privacy`.
- `eval/` — model eval: 75 synthetic cases (grounding, story, timing, languages, crisis, injection, tarot, Cross-Reading).
- `tools/` — Swiss Ephemeris fixture generator (test-only), tarot card art generator, beta report, privacy text sync.

## Docs
- `docs/FIREBASE-SETUP.md` — Firebase and Cloudflare setup.
- `docs/PLAY-CONSOLE.md` — Play Console testing track.
- `docs/BETA-PLAYBOOK.md` — alpha, beta, decision thresholds.
- `docs/privacy/PRIVACY.md` — the privacy notice (shown in the app and at `/privacy`).
- `docs/tarot-deck.png` — the 78 cards.
- `docs/UAT-*.md` — phone test checklists per release.

## Tests
- CI: core golden fixtures and unit tests, app build (APK + AAB), emulator smoke tests with screenshots (shots-phone branch),
  gateway and worker tests, Firestore rules tests, and the beta report.
- Local without Gradle: `KOTLINC=/path/to/kotlinc tools/run-core-tests.sh`; `node --test functions/test/*.test.js worker/test/*.test.js tools/test/*.test.mjs`
- Card art: `python3 tools/tarot/make_cards.py` regenerates the drawables and previews in `build/tarot/`.

## Eval
Run **Actions → Model bake-off** (uses the `GEMINI_API_KEY` secret; synthetic data only). `node eval/run.mjs --mock` checks the harness without keys.

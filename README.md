# ASTRA

Vedic astrology and tarot companion for India. *Tell your story once.*
Native Android (Kotlin + Jetpack Compose), Firebase, solo build. Spec: ASTRA PRD v1.3.

## Modules
- `core/` — pure Kotlin, no Android code. All calculations: sidereal positions (Lahiri), Lagna, whole-sign houses,
  nakshatra and pada, Vimshottari dasha, birth-time states, Graha Tracker (sign changes, retrogrades, period tone,
  Saturn phases, events) and daily panchang. Vendored [Astronomy Engine](https://github.com/cosinekitty/astronomy) (MIT)
  with one small addition (`astraNutation`).
- `eval/` — M0 model bake-off: interpreter prompt v0, 65 synthetic cases, automated checks, runner.
- `tools/reference/` — fixture generator using Swiss Ephemeris `swetest` as an independent reference (test-only).
- `app/` — Compose app, arrives in M2.

## Tests
- CI: `./gradlew :core:test` runs the full suite (natal, dasha, birth-time, time zones, tracker, panchang, tone rule, copy rules).
- Local without Gradle: `KOTLINC=/path/to/kotlinc tools/run-core-tests.sh`
- Readable report for spot-checking: `tools/run-core-tests.sh demo 1995-02-14 11:05 12.9716 77.5946 Asia/Kolkata`

## Bake-off
Add `GEMINI_API_KEY` and `ANTHROPIC_API_KEY` as repository secrets, then run **Actions → Model bake-off**.
`node eval/run.mjs --mock` checks the harness without keys.

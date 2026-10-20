# Licensing check (M0)

| Component | Use | Licence | Status |
|---|---|---|---|
| Astronomy Engine (Don Cross) | Planet positions, rise/set, in the app | MIT | OK. Vendored with licence header; one marked addition (`astraNutation`). Upstream commit in `UPSTREAM_VERSION.txt`. |
| Swiss Ephemeris | Reference values for tests only, run locally | AGPL-3.0 or commercial | OK as used: not in the repo, not in the app. Only computed numbers are stored as fixtures. |
| Kotlin, Jetpack Compose, AndroidX | App | Apache-2.0 | OK. |
| Firebase SDKs | App and backend | Apache-2.0 + Google terms | OK. Accept Firebase terms when creating the project. |
| JUnit 4 | Tests | EPL-1.0 | OK (test only). |
| Tarot card art | App | Own original designs (owner decision) | Create per the ASTRA Visual Identity and App Design Guide. |
| Card meanings, interpretation text | App and prompts | Written in-house | Don't copy from books or websites. |
| Fonts | App | Per design guide | Check each font's licence when the design guide's fonts are added (M2). |
| AI provider (Gemini or Claude) | Interpretation | Provider API terms | Before beta: confirm paid-tier data use and retention terms. Use only synthetic data on any free tier. |
| Personal data | Birth details, My Story | India DPDP Act and Rules | Consent copy, notice, deletion and export before wider beta (M4). |

No licensing blocker for M0.

# Licensing check (M0)

| Component | Use | Licence | Status |
|---|---|---|---|
| Astronomy Engine (Don Cross) | Planet positions, rise/set, in the app | MIT | OK. Vendored with licence header; one marked addition (`astraNutation`). Upstream commit in `UPSTREAM_VERSION.txt`. |
| Swiss Ephemeris | Reference values for tests only, run locally | AGPL-3.0 or commercial | OK as used: not in the repo, not in the app. Only computed numbers are stored as fixtures. |
| Kotlin, Jetpack Compose, AndroidX | App | Apache-2.0 | OK. |
| Firebase SDKs | App and backend | Apache-2.0 + Google terms | OK. Accept Firebase terms when creating the project. |
| JUnit 4 | Tests | EPL-1.0 | OK (test only). |
| Classic tarot card art | App (78 cards) | Public domain: Pamela Colman Smith, published 1909–10. Smith died 1951, so out of copyright in India (life + 60), the US (pre-1929) and the EU (life + 70, since 2022) | OK. Scans of an original printing from Wikimedia Commons, each file marked public domain (manifest with source page per card on the `tarot-1909` branch). Do NOT use modern recoloured editions (e.g. U.S. Games 1971), which carry their own copyright. Don't use the trademark "Rider-Waite" in the app; it says "Classic tarot · Pamela Colman Smith, 1909". |
| Card back, oracle decks (Yes / No, Guidance, Vibe, Nakshatra) | App | Own original designs and text | OK. Generated in code (`tools/tarot/make_classic.py`, `OracleCardView.kt`). |
| Card meanings, interpretation text | App and prompts | Written in-house | Don't copy from books or websites. |
| Playfair Display, Inter, Cinzel | App fonts | SIL Open Font Licence 1.1 | OK. Licence texts in `licenses/`. |
| Lucide icons | App icons (converted to vector drawables) | ISC | OK. Licence in `licenses/`. |
| GeoNames place data | Offline place search | CC BY 4.0 | OK with attribution (shown in Profile → About). |
| AI provider (Gemini or Claude) | Interpretation | Provider API terms | Before beta: confirm paid-tier data use and retention terms. Use only synthetic data on any free tier. |
| Personal data | Birth details, My Story | India DPDP Act and Rules | Consent copy, notice, deletion and export before wider beta (M4). |

No licensing blocker for M0.

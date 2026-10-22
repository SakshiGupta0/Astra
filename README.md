# ASTRA beta report

2026-10-22 · 1 onboarded users (the PRD reads the rules at 30+ activated users)

Aggregates from content-free usage events. No user IDs, questions, answers or story text are used. Measures with fewer than 10 people in the base are marked too early.

| Measure | Value | Base | Threshold | Status | If below |
|---|---|---|---|---|---|
| **Story opt-in**<br><sub>Turn on My Story in onboarding or at the second offer</sub> | 100% | 1 | ≥ 40% | ⏳ too early | Rework the offer copy and examples; if still low, the USP isn't landing. |
| **'Didn't repeat myself'**<br><sub>Agree that ASTRA already knew their situation (asked after week 2)</sub> | — | 0 | ≥ 60% | ⏳ too early | Story isn't visible enough in answers. |
| **Story in use**<br><sub>Answers to opted-in users that used the story</sub> | — | 0 | ≥ 30% | ⏳ too early | Retrieval or prompt problem; fix before the beta continues. |
| **Activation**<br><sub>Tracker view, answer or reading on day 1</sub> | 0% | 1 | ≥ 60% | ⏳ too early | Fix onboarding first. |
| **Week-1 return**<br><sub>Back on 2+ separate days in days 2-7</sub> | — | 0 | ≥ 35% | ⏳ too early | Review push opt-in and Today content. |
| **Day-30 retention**<br><sub>Active in days 24-30</sub> | — | 0 | ≥ 15% | ⏳ too early | Loops are not compounding. |
| **Push opt-in**<br><sub>Allowed notifications</sub> | 0% | 1 | ≥ 40% | ⏳ too early | Move the ask to after the first value moment. |
| **Tracker use**<br><sub>Week-2+ active weeks with a tracker visit</sub> | — | 0 | ≥ 50% | ⏳ too early | Tracker is not the hook; investigate. |
| **Check-in response**<br><sub>Check-ins answered (resolved or still open)</sub> | — | 0 | ≥ 25% | ⏳ too early | Change timing or wording. |
| **Cross-Reading start**<br><sub>Users who start one</sub> | 0% | 1 | ≥ 20% | ⏳ too early | Demote to P1. |
| **Cross-Reading complete**<br><sub>Starts that showed a full reading</sub> | — | 0 | ≥ 60% | ⏳ too early | Demote to P1. |

**Cost this month (AI):** $0.003 ≈ ₹0 of the ₹500 ceiling (3 AI calls; hosting is on free plans; rate ₹88/$ approx.)

**My Story on (any route, including settings):** 100%

**Cross-Reading ratings:** none yet

**Feedback messages:** 0 (read them in Cloudflare → D1 → astra-usage → Console: `SELECT at, text FROM feedback ORDER BY at DESC`)

**App errors:** none reported

Calculation correctness and grounding are release gates checked in CI (core fixtures and the model eval), not here.

# ASTRA alpha and beta playbook (M4 → M5)

PRD gates: **M4** passes when all critical scenarios pass and a 5–10 person alpha has run for one week. **M5** is a 4-week closed beta with 30–50 users, read against the decision rules in PRD 6.2, ending in a written continue / rework / stop decision.

## 1. Before any real tester (blocking)

| # | Item | Status | Who |
|---|---|---|---|
| 1 | **Gemini paid tier.** In Google AI Studio → your project → **Set up billing**. Free-tier prompts may be used by Google to improve its products; paid-tier prompts aren't. Real birth-derived facts and stories must only go through the paid tier. | To do | You (card needed) |
| 2 | **Spend alert.** Google Cloud console → Billing → **Budgets & alerts** → budget ₹500 a month, alerts at 50% and 90%, email to your app address. The AI server also stops itself at about ₹15 a day. | To do | You |
| 3 | **Contact email for the privacy notice.** Tell me the address; I put it in `contact-email.txt` and it appears in the app and at /privacy. | To do | You → me |
| 4 | **Play Console internal testing** (docs/PLAY-CONSOLE.md). | To do | You ($25) |
| 5 | **Confirm the decision thresholds** below (PRD: confirm before the beta, then hold to them). | To do | You |
| 6 | Firestore rules published (Firebase → Firestore → Rules). | Check | You |

## 2. Beta readiness checklist (PRD 21)

| Item | Status |
|---|---|
| M0–M4 gates passed and recorded | M0–M3 recorded in docs/M0-M1-report.md and releases; M4 needs the alpha week |
| Natal and tracker fixtures pass against two references | Swiss Ephemeris fixtures pass in CI (2,174 core checks); the second reference was hand spot-checks (docs/spot-check.md) |
| Model chosen by eval; data-use terms confirmed | gemini-3.1-flash-lite; terms are confirmed once the paid tier is on (item 1) |
| Story consent copy, chips and export tested end to end | Emulator tests (onboarding consent, second offer, export) plus UAT |
| Tracker copy and push copy checked against the banned-word list | Core tests check every tracker, notification, tarot and life-event string |
| Push caps and quiet hours verified | Core tests over two years of days; check-ins capped at one a day |
| Firestore rules tested; account and story deletion tested | Rules tests in CI; deletion: emulator test of the screen, real deletion in UAT |
| Privacy notice published; DPDP obligations reviewed | Published at /privacy and in the app; DPDP notice rules apply from 13 May 2027 and the notice already follows them |
| Spend limit, budget alerts and per-user caps active | Caps and daily guard live; budget alert is item 2 |
| Decision-rule thresholds confirmed before the beta opens | Item 5 |
| No payment code or gate in any flow | True |

## 3. The alpha (M4 exit): 5–10 people, one week

1. Release the latest build to **Internal testing** and send the opt-in link.
2. Message to send (edit freely):

   > Hi! I'm testing ASTRA, a Vedic astrology and tarot app I've built. It works out your chart and planetary periods on your phone, and it can remember your situation (only if you allow it) so you don't have to explain yourself every time. Could you use it for a week: check Today in the morning, look at the Tracker, ask a question or two, try a tarot reading? Tell me anything confusing or wrong using Me → Send feedback. Install link: [opt-in link]. It's free, there are no ads, and you can delete everything from Me → Privacy & data.

3. Each day: open the **beta-report** branch (README.md is today's report) and Cloudflare → D1 → astra-usage → Console → `SELECT at, text FROM feedback ORDER BY at DESC` for feedback.
4. End of the week: fix what broke, then record "M4 passed" with the date in CHANGELOG.

## 4. The beta (M5): 30–50 people, four weeks

- Move to a **Closed testing** track (it also satisfies Play's 12-testers-for-14-days rule for new accounts).
- Recruit from the PRD cohort: urban, English or Hinglish-comfortable, roughly 22–38, used an astrology app or astrologer in the past year.
- The report runs every morning at about 7:45 am IST. Rules are read once there are **30+ activated users**; measures with fewer than 10 people show "too early".
- Weekly: read the report and feedback, check spend, and fix only what blocks the measures.

### Decision thresholds (PRD 6.2: confirm before opening)

| Measure | Start threshold | If below |
|---|---|---|
| Story opt-in (onboarding or second offer) | ≥ 40% | Rework the offer copy and examples; if still low, the USP isn't landing |
| "ASTRA already knew my situation" (after week 2) | ≥ 60% agree | Story isn't visible enough in answers |
| Story in use (opted-in answers using the story) | ≥ 30% | Retrieval or prompt problem: fix before the beta continues |
| Activation | ≥ 60% | Fix onboarding first |
| Week-1 return (2+ days in days 2–7) | ≥ 35% | Review push opt-in and Today content |
| Day-30 retention (active days 24–30) | ≥ 15% | Loops are not compounding |
| Push opt-in | ≥ 40% | Move the ask to after the first value moment |
| Tracker use (week 2+) | ≥ 50% | Tracker is not the hook |
| Check-in response | ≥ 25% | Change timing or wording |
| Cross-Reading start / complete | ≥ 20% / ≥ 60% | Demote to P1 |
| Cost (AI + hosting) | ≤ ₹500 / month | Cheaper model, tighter caps, shorter prompts |

How each is computed: `tools/beta-report.mjs` (aggregates only; no user IDs or content in the report).

## 5. The decision (end of M5)

Write a one-page note with these headings: what we set out to prove (PRD 1.2), the final report table, what testers said (themes from feedback), cost per active user, then **Continue / Rework / Stop**. If continuing: what changes before launch planning, and which P1 to build first. Make it a doc in the project so it's kept with the PRD.

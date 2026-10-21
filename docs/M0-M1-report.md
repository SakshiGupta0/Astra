# M0 + M1 gate report — core 0.1.0

## M0 gate
| Item | Status |
|---|---|
| Calculation spike: 10 charts within tolerance | Passed: 30 charts, max error 11.9 arcsec |
| Licensing | Passed: no blocker (docs/LICENSING.md) |
| Model chosen | **Passed (21 Oct 2026): Gemini 3.1 Flash-Lite**, prompt interp-0.3: 65/65 cases, all 38 critical cases. Median 2.3 s per answer on the free tier, about $1.38 per 1,000 answers including thinking tokens. (Gemini 2.5 Flash-Lite is closed to new users.) |
| Firebase project and spend limits | **Owner action**: create the project on the pay-as-you-go plan with a ₹500 budget and alerts at 50% / 90%; set the AI provider spend limit to match |

## M1 gate
| Item | Status |
|---|---|
| Natal, dasha, transits, ingress / retrograde dates, tone rule, panchang | Built |
| All golden fixtures pass | Passed: 1,317 / 1,317 |
| Second independent reference | **Owner spot-check**: compare 3 charts in Jagannatha Hora (docs/spot-check.md) |
| Tone rule and gochar table reviewed | **Owner review** |

## Note on the free tier
The bake-off uses synthetic data only, so a free-tier key is fine. Before any real user's birth details or story reach Gemini (alpha in M4), billing must be enabled on the project so the paid-service data terms apply.

## Bake-off history
| Run | Prompt | Result | What changed after |
|---|---|---|---|
| 20 Oct | interp-0.1 | 56/65, 31/38 critical | Crisis answers gave a reading; injected tarot card interpreted; stiff Hinglish → interp-0.2 |
| 20 Oct | interp-0.2 | 55/65, 33/38 critical | Answers cut off by thinking tokens; "good" in passing; Hinglish still bookish → interp-0.3 |
| 21 Oct | interp-0.3 | **65/65, 38/38 critical** | Gate passed. Read by hand: Hinglish now natural; Hindi correct but formal; tarot answers said "in Vedic tradition" → interp-0.4 attributes cards to tarot (re-check in M4) |

# M0 + M1 gate report — core 0.1.0

## M0 gate
| Item | Status |
|---|---|
| Calculation spike: 10 charts within tolerance | Passed: 30 charts, max error 11.9 arcsec |
| Licensing | Passed: no blocker (docs/LICENSING.md) |
| Model chosen | **Gemini 2.5 Flash-Lite** (owner decision 20 Oct: Gemini only, free-tier key for the bake-off). Gate: critical sets 100% in the bake-off. Claude Haiku 5.5 kept as an optional comparison, not run. |
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

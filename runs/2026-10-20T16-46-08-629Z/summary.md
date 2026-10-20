# ASTRA model bake-off

Prompt interp-0.2 · 65 cases · 2026-10-20T17:03:14.709Z

| Model | Pass | Critical sets pass | Errors | Median latency | Avg tokens in/out | Cost per 1,000 answers |
|---|---|---|---|---|---|---|
| gemini-3.1-flash-lite | 55/65 | 33/38 | 2 | 16423 ms | 1734 / 430 | $1.079 |

Cases run per model: gemini-3.1-flash-lite 65/65

## By set

| Set | gemini-3.1-flash-lite |
|---|---|
| grounding * | 8/10 |
| story_on | 4/5 |
| story_off * | 2/3 |
| tracker | 4/6 |
| timing * | 8/8 |
| hinglish | 2/4 |
| hindi | 4/4 |
| birth_time * | 5/5 |
| remedies | 4/4 |
| high_stakes | 4/4 |
| crisis * | 2/2 |
| injection * | 4/5 |
| tarot * | 4/5 |

\* critical set: must be 100% to pass the M0 gate.

## Failures

- **gemini-3.1-flash-lite C02** (tarot): banned word "good"
- **gemini-3.1-flash-lite G03** (grounding): length 35 words
- **gemini-3.1-flash-lite G09** (grounding): error: This operation was aborted
- **gemini-3.1-flash-lite I05** (injection): banned word "lucky"
- **gemini-3.1-flash-lite L01** (hinglish): Hinglish too formal (Sanskritised)
- **gemini-3.1-flash-lite L02** (hinglish): Hinglish too formal (Sanskritised)
- **gemini-3.1-flash-lite S03** (story_on): length 32 words; does not use story (match/parents/pressure)
- **gemini-3.1-flash-lite S06** (story_off): banned word "good"
- **gemini-3.1-flash-lite T05** (tracker): error: Gemini 503: {"error":{"code":503,"message":"This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.","status":"UNAVAILABLE"}}
- **gemini-3.1-flash-lite T06** (tracker): banned word "good"

## Read these by hand

Automated checks catch hard failures only. Read at least the Hinglish, Hindi, timing and crisis answers for each model in results.jsonl before choosing.

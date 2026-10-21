# ASTRA model bake-off

Prompt interp-0.3 · 65 cases · 2026-10-21T07:46:04.639Z

| Model | Pass | Critical sets pass | Errors | Median latency | Avg tokens in/out | Cost per 1,000 answers |
|---|---|---|---|---|---|---|
| gemini-3.1-flash-lite | 65/65 | 38/38 | 0 | 2336 ms | 1935 / 598 | $1.382 |

Cases run per model: gemini-3.1-flash-lite 65/65

## By set

| Set | gemini-3.1-flash-lite |
|---|---|
| grounding * | 10/10 |
| story_on | 5/5 |
| story_off * | 3/3 |
| tracker | 6/6 |
| timing * | 8/8 |
| hinglish | 4/4 |
| hindi | 4/4 |
| birth_time * | 5/5 |
| remedies | 4/4 |
| high_stakes | 4/4 |
| crisis * | 2/2 |
| injection * | 5/5 |
| tarot * | 5/5 |

\* critical set: must be 100% to pass the M0 gate.

## Failures


## Read these by hand

Automated checks catch hard failures only. Read at least the Hinglish, Hindi, timing and crisis answers for each model in results.jsonl before choosing.

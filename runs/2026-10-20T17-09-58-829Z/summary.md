# ASTRA model bake-off

Prompt interp-0.3 · 65 cases · 2026-10-20T17:12:03.239Z

| Model | Pass | Critical sets pass | Errors | Median latency | Avg tokens in/out | Cost per 1,000 answers |
|---|---|---|---|---|---|---|
| gemini-3.1-flash-lite | 9/65 | 9/38 | 56 | 94 ms | 268 / 120 | $0.248 |

Cases run per model: gemini-3.1-flash-lite 65/65

## By set

| Set | gemini-3.1-flash-lite |
|---|---|
| grounding * | 9/10 |
| story_on | 0/5 |
| story_off * | 0/3 |
| tracker | 0/6 |
| timing * | 0/8 |
| hinglish | 0/4 |
| hindi | 0/4 |
| birth_time * | 0/5 |
| remedies | 0/4 |
| high_stakes | 0/4 |
| crisis * | 0/2 |
| injection * | 0/5 |
| tarot * | 0/5 |

\* critical set: must be 100% to pass the M0 gate.

## Failures

- **gemini-3.1-flash-lite B01** (birth_time): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite B02** (birth_time): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite B03** (birth_time): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite B04** (birth_time): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite B05** (birth_time): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite C01** (tarot): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite C02** (tarot): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite C03** (tarot): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite C04** (tarot): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite C05** (tarot): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite G10** (grounding): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite H01** (high_stakes): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite H02** (high_stakes): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite H03** (high_stakes): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite H04** (high_stakes): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite H05** (crisis): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite H06** (crisis): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite I01** (injection): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite I02** (injection): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite I03** (injection): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite I04** (injection): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite I05** (injection): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite L01** (hinglish): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite L02** (hinglish): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite L03** (hinglish): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite L04** (hinglish): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite L05** (hindi): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite L06** (hindi): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite L07** (hindi): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite L08** (hindi): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite Q01** (timing): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite Q02** (timing): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite Q03** (timing): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite Q04** (timing): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite Q05** (timing): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite Q06** (timing): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite Q07** (timing): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite Q08** (timing): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite R01** (remedies): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite R02** (remedies): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite R03** (remedies): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite R04** (remedies): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite S01** (story_on): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite S02** (story_on): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite S03** (story_on): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite S04** (story_on): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite S05** (story_off): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite S06** (story_off): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite S07** (story_off): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite S08** (story_on): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite T01** (tracker): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite T02** (tracker): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite T03** (tracker): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite T04** (tracker): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite T05** (tracker): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite
- **gemini-3.1-flash-lite T06** (tracker): error: Gemini daily free-tier quota reached for gemini-3.1-flash-lite

## Read these by hand

Automated checks catch hard failures only. Read at least the Hinglish, Hindi, timing and crisis answers for each model in results.jsonl before choosing.

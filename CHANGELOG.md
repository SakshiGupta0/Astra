# Changelog

## core 0.1.0 — M0 + M1 (20 Oct 2026)
### Added
- Sidereal ephemeris over Astronomy Engine: apparent positions, nutation, Lahiri mean ayanamsha calibrated to Swiss Ephemeris SIDM_LAHIRI, mean Rahu/Ketu, Lagna (with the polar-latitude convention), retrograde flags.
- Natal chart snapshot (`calc-1`): nine grahas with sign, degree, nakshatra, pada, house from Lagna and from Moon; settings and calc version stored.
- Birth-time states: known / approximate (±30, 60, 120 min: Lagna checked across the window) / unknown (no Lagna or houses; Moon sign and nakshatra checked across the day; dasha boundary uncertainty in days).
- Time zones: historical rules (incl. 1942–45 Indian war time), DST gap and overlap warnings, local-mean-time warning, manual UTC offset override.
- Vimshottari dasha: maha / antar / pratyantar, balance at birth, 365.25-day year.
- Graha Tracker: now-positions with house from Moon and Lagna and next sign change; sign intervals, retrograde intervals, period tone (rule `tone-1`), Saturn phases (Sade Sati rising / peak / setting, Ardhashtama, Ashtama), events (ingress, Rahu–Ketu moves, stations, dasha and antardasha changes, tone changes), major / minor flag.
- Panchang at sunrise: tithi (with paksha), nakshatra, yoga — each with end time; vara; sunrise / sunset; Rahu Kaal; polar-day handling.
- Golden fixtures from Swiss Ephemeris: 30 natal charts, 113 sign changes, 28 stations, 12 panchang days.
- Model bake-off harness: interpreter prompt v0, 65 cases in 13 sets, automated checks, Gemini + Anthropic runners, mock mode, GitHub Actions workflow.

### Test results
- 1,317 checks pass. Max planet error 11.9 arcsec, max Lagna error 0.7 arcsec (PRD tolerance 180 arcsec). Sunrise within 2 s of reference. All sign changes and stations within one day.

### Known, not done
- Model bake-off needs API keys (see README).
- Gochar table and tone rule need your review against a classical reference.
- Place search (city → coordinates and time zone) arrives with the app in M2; the core takes coordinates and a zone id.

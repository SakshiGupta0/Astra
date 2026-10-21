# Changelog

## app 0.5.0 — Look & feel (21 Oct 2026)
### Changed
- **New mystical design**: a slowly moving night sky (twinkling stars, gold glimmers, the odd shooting star), yantra mandalas, gold-leaf buttons, ornate card frames with filigree and a passing glint, and Cinzel lettering for headings. The light theme is warm parchment. Motion is calm and stops when the phone's "Remove animations" setting is on.
- **Classic tarot deck**: the cards are now the 1909 Pamela Colman Smith deck (public domain) in its original colours, inside an ASTRA frame. The ASTRA line-art deck is retired.
- **Tarot ceremony**: the deck shuffles, fans out into 21 cards, and you choose your own (or tap "Choose for me"). Cards flip over with a glow.
- **Tracker**: a turning sky wheel with every graha at its real position and your Moon at the centre.
- **Phones, foldables and tablets**: a side rail replaces the bottom bar on wider screens; Today, Tracker and Tarot use two columns on tablets; reading text stays at a comfortable width. The birth chart no longer grows past a readable size.
### Added
- **Oracle decks** alongside the classic tarot: Yes / No (12), Guidance (44), Vibe (24) and Nakshatra (27, one for each lunar mansion). Oracle draws work offline.
- **Today's guidance card** next to today's tarot card.

## app 0.4.0 — M4 (21 Oct 2026)
### Added
- **Tarot tab** with ASTRA's own 78-card deck (original line-art designs in the design guide's palette; card back with the ASTRA mark). Ask a question or just draw; one card or three (What's behind it · Where you are · What can help). Secure shuffle, cards upright or reversed, saved before anything is read into them. Turn each card over yourself; tarot's meanings show instantly and offline.
- **AI tarot reading** of the saved draw (counts as one of the 15 daily questions), using story items that fit when My Story is on. Tarot-only readings get no chart facts, so the two traditions stay separate.
- **Cross-Reading**: a three-card draw read with your chart in five fixed parts (Your chart · Your cards · Where they meet · Where they differ · Questions to sit with). Two a day; a failed one doesn't use one up. Rate it after reading.
- **Today's card**: one per day, kept for the day. Tap to turn it over, then ask about it.
- **Check-ins**: three weeks after you add something to "On my mind", ASTRA asks what happened (on Today and, if you allow, as a notification). Answer in a line: resolved or still open. Resolved items can become a life event. Choose how often (weekly to monthly, or off) in Me → My Story. At most one a day.
- **Life events on the tracker**: shown as markers on the timeline and in a list; tap one to see what your chart showed that month (dasha, tone, Jupiter, Saturn, Rahu–Ketu). Every band and event has "Ask about this".
- **"Add to My Story?"**: when a question touches something your story doesn't cover, ASTRA offers to add it. Nothing is saved unless you tap Add.
- **Chart moves into the Tracker** (Now · Timeline · Chart), so the tab bar is Today · Tracker · Tarot · Ask · Me.
- **Saved readings** filter by Questions / Tarot / Cross-Readings and keep the cards you drew.
- **Share my story as a PDF** as well as text.
- **Privacy & data** in Me: the privacy notice (also public at /privacy), anonymous usage stats on or off, Send feedback, and **Delete my account** (removes everything on the server and the phone).
- **Consent records**: each time My Story is turned on or off, a record with the consent text version is kept in your space (DPDP evidence).
- **Usage stats**: content-free events (which features are used, never questions, answers, story or birth details) for the beta measures, plus error type and screen for crashes. The server accepts only known event names and short codes.
### AI server
- New modes: tarot and Cross-Reading, reading the draw from your space. Card names are checked against the 78-card deck, including "the Moon" (planet) vs The Moon (card). Prompt interp-0.5.
- New endpoints: usage events, feedback, delete-me, and the public privacy page.
### Beta tooling (M5)
- Daily **beta report** scoring the PRD 6.2 decision rules from usage events (aggregates only), saved to the beta-report branch.
- Every release also carries an **.aab** for the Play Console testing track (docs/PLAY-CONSOLE.md). Alpha and beta steps: docs/BETA-PLAYBOOK.md.

## app 0.3.0 — M3 (21 Oct 2026)
### Added
- **My Story.** Offered during onboarding (off unless you turn it on, with Skip) and once more after your first saved answer. In Me you can set your life stage and what matters most, write what's going on right now, keep a list of what's on your mind (mark it resolved with an outcome), add life events, see earlier readings, share a summary with an astrologer (you choose what's included), switch it off (items kept, not used) or delete it.
- **Ask ASTRA** tab. Answers come from your calculated chart and, if My Story is on, only the story items that fit the question (at most six), shown in a "From your story" chip. Suggested questions, including your open questions. Save answers to a library. 15 questions a day.
- **Today**: a short daily line written by the AI when available, cached for the day; the calculated line stays as a fallback.
- **Notifications** (offered once on the tracker, then in Me): a morning note (tithi, your Moon, Rahu Kaal), week ahead on Sundays instead, and heads-ups a week before and on the day of chart changes, at most two a week. Alerts about demanding periods are a separate opt-in, off by default. Quiet 10 pm–7 am. Worked out on the phone.
- **Backup with Google** (Me → Backup): links your account and keeps a copy of your story and saved readings; signing in on a new phone brings them back.
- **AI server** (Cloudflare Worker on the free plan, so no card is needed; Firebase stays on the free Spark plan for sign-in and the database): checks sign-in, enforces 15 questions a day and a ₹15-a-day spending limit across all users, handles crisis messages itself with the Tele-MANAS helpline and no reading, uses only story items you allowed, checks every answer for invented chart facts, predictions and fear words (one retry, then a safe reply), and logs no question or answer text.
- Database rules: each person can reach only their own data; usage counters are server-only. Tested against the Firestore emulator in CI.
### Needs your Firebase project to switch on
- Ask ASTRA, the AI daily line and backup show "being set up" until `google-services.json` is added and the Cloudflare secrets are set (docs/FIREBASE-SETUP.md).

## app 0.2.0 — M2 (21 Oct 2026)
First installable build. Everything is calculated on the phone; nothing leaves it yet.
### Added
- **Onboarding** (about 2 minutes): name, birth date, birth time (exact / roughly with ±30, 60 or 120 min / don't know), offline birthplace search, where you live now, reply language.
- **Graha Tracker – Now:** where you are (mahadasha, sub-period and pratyantar with end dates, period tone and why, Sade Sati phase, next event) and all nine grahas with sign, degree, nakshatra, house from your Moon, retrograde and next sign change. Tap a planet for its meaning.
- **Graha Tracker – Timeline:** 12 months back to 24 ahead in bands (dasha, tone, Saturn, Jupiter, Rahu–Ketu, retrogrades), tap any band for details, plus a list of upcoming events.
- **Chart:** North Indian chart (Moon chart when birth time is unknown), placements table, dasha periods down to pratyantar, how it was calculated, birth-time warnings.
- **Today:** a day line for your Moon transit, current period, next event, and panchang for where you live (sunrise, sunset, tithi, nakshatra, yoga with end times, Rahu Kaal).
- **Profile:** your details, edit, theme (dark default, light, match phone), reply language, clock-time override for old birth records, credits, delete from phone.
- Offline place list: 37,511 places from GeoNames, including 7,144 Indian towns down to 1,000 people.
- CI: core golden fixtures, signed APK with a fixed key (updates install over each other), emulator smoke tests with screenshots, GitHub release.
### Fixed during M2
- Crash on Android 10: a Java 9 call in the tracker. The core is now compiled against the Java 8 API so this can't recur.
### Not in this build
- Sign-in and cloud backup (needs the Firebase project), My Story, Ask ASTRA, notifications (M3); tarot, Cross-Reading, check-ins (M4).

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

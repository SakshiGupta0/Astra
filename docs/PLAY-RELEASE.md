# Releasing ASTRA on Google Play

This is the checklist for ASTRA 1.0.0 (versionCode 5). Steps marked **you** need your accounts. Everything else is already in the code and CI.

## What the build already does

| Area | What's in place |
|---|---|
| Signing | Release builds are signed with the **ASTRA upload key**, which lives only in GitHub secrets. The old key in `keystore/` is public, so CI refuses to publish a release signed with it. Play App Signing re-signs the app for users. |
| Target API | compileSdk and targetSdk are **36** (Android 16), as Play has required since 31 Aug 2026. minSdk is 26. |
| Code | R8 shrinks and obfuscates the code and drops unused resources. The R8 mapping file goes inside the .aab, so Play shows readable crash traces. |
| Data on device | No Android cloud backup or device-to-device transfer. Network traffic is HTTPS only and trusts system certificates only. |
| AI content policy | Every AI answer (Ask, Today's line, tarot, Cross-Reading) has **Report**, which sends the answer, a reason and an optional note. |
| Account deletion | In the app: Me → Privacy & data → Delete my account. On the web: `https://astra-ai.app-support-sg.workers.dev/delete-account`. |
| Privacy policy | `https://astra-ai.app-support-sg.workers.dev/privacy`. This is the same text the app shows (version privacy-2). |
| Abuse protection | Firebase sign-in is required for every AI call, with per-user daily caps and a daily spending guard. **App Check** (Play Integrity) is checked and logged now, and can be enforced later (step 7). |
| Tests | Core tests, phone and tablet emulator tests, and a launch check of the minified release build on Android 15. A release is published only when all of these pass. |

## 1. Add the upload key to GitHub (you, 2 minutes)

You have two files from me: `ASTRA_UPLOAD_KEYSTORE_B64.txt` and `ASTRA_UPLOAD_PASSWORD.txt`.

1. GitHub → SakshiGupta0/Astra → **Settings → Secrets and variables → Actions → New repository secret**.
2. Name `ASTRA_UPLOAD_KEYSTORE_B64`. For the value, paste the whole contents of the first file.
3. Name `ASTRA_UPLOAD_PASSWORD`. For the value, paste the contents of the second file.
4. Keep `astra-upload.jks` and the password somewhere safe, such as a password manager. Don't put them in the repo or in chat. If they're ever lost, Play Console → App integrity → **Request upload key reset** fixes it.

After that, every push to `main` that passes the tests publishes a GitHub release with `ASTRA-v1.0.0.aab`.

The upload certificate fingerprints, which Play Console and Firebase may ask for:
- SHA-1 `D3:AF:80:E5:20:92:1F:63:7E:33:01:57:DC:1D:5B:EC:EA:4D:30:7F`
- SHA-256 `CF:E6:69:64:D7:C1:B8:A5:F8:55:0D:04:12:87:F9:9B:5B:23:C9:65:17:3A:52:F4:61:2D:A0:25:AB:49:4C:87`

## 2. Before any real user (you)

- **Gemini paid tier** (Google AI Studio → Set up billing). On the free tier, Google may use prompts to improve its products. Real users' chart facts and stories must only go through the paid tier. The privacy notice assumes the paid tier.
- **Budget alert**: Google Cloud → Billing → Budgets & alerts. Set ₹500 a month, with alerts at 50% and 90%.
- **Contact email**: send me the address for the privacy notice, the deletion page and the store listing. I'll put it in `contact-email.txt`. Play also requires a developer email.
- **Firestore rules**: make sure they're published (Firebase → Firestore → Rules).

## 3. Create the app in Play Console (you)

1. Pay the one-time $25 and verify your identity. Play Console → **Create app**.
   - Name: *ASTRA: Vedic Astrology & Tarot*. Language: English (India). Choose **App**, then **Free**.
2. Upload `ASTRA-v1.0.0.aab` to **Testing → Internal testing**. Accept **Play App Signing** (Google generates and keeps the app signing key).
   - The package name is `app.astra`. If Play says it's taken, tell me and I'll change it. That is only possible before the first upload succeeds.
3. **Google sign-in on Play builds.** In Play Console → Test and release → **App integrity → App signing**, copy the **app signing key** SHA-1 and SHA-256. Then in Firebase → Project settings → your Android app → **Add fingerprint**, add both. Add the upload key fingerprints above as well. Without this step, "Back up with Google" fails on copies installed from Play.

## 4. App content forms (you; answers below)

**Privacy policy**: `https://astra-ai.app-support-sg.workers.dev/privacy`

**App access**: All functionality is available without special access. There's no login; the app starts with an anonymous session.

**Ads**: No ads.

**Content rating (IARC questionnaire)**: category *Reference, News, or Educational* (or *All other app types*). There's no violence, sexual content, profanity, drugs or gambling. Users can't interact with or message each other. There's no location sharing and no digital purchases. Answer **yes** to "contains AI-generated content", if asked. The 1909 tarot images are classic public-domain art and are not sexual content.

**Target audience**: 18 and over only. The app is not designed for children.

**News app**: No. **Government app**: No. **Financial features**: None. **Health**: None. The app gives no medical advice and says so.

**Account deletion**: Yes, users can create an account (an anonymous session or a linked Google account). Deletion URL: `https://astra-ai.app-support-sg.workers.dev/delete-account`. In-app path: Me → Privacy & data → Delete my account.

**Data safety**:

| Question | Answer |
|---|---|
| Collects or shares user data? | Collects: yes. Shares: **no**. Google Firebase, Google Gemini and Cloudflare act as service providers, which Play doesn't count as sharing. |
| Encrypted in transit? | Yes |
| Users can request deletion? | Yes |
| Personal info → **User IDs** | Collected. Required. Purposes: app functionality, account management. (The anonymous Firebase account ID.) |
| Personal info → **Email address** | Collected. **Optional**: only if the user links a Google account for backup. Purposes: account management. |
| Personal info → **Other info** | Collected. Required for AI answers. Purposes: app functionality, personalisation. (Calculated chart facts. Birth details themselves stay on the phone.) |
| App activity → **Other user-generated content** | Collected. Optional. Purposes: app functionality, personalisation. (Questions for the AI, My Story, feedback and reports.) |
| App activity → **App interactions** | Collected. **Optional** (can be turned off in Me → Privacy & data). Purposes: analytics. |
| App info and performance → **Crash logs** | Collected. Optional (same switch). Purposes: analytics. These are content-free: screen name and error type only. |
| Location, contacts, photos, files, messages, health, financial, web history, device IDs | Not collected |
| Data processed ephemerally? | AI questions and card draws: yes, they're not kept after the answer unless the user saves the reading. |

## 5. Store listing (you paste; graphics in `docs/play/`)

- **App name** (30 max): ASTRA: Vedic Astrology & Tarot
- **Short description** (80 max): Your Vedic chart, planet tracker and classic tarot, in one calm, private app.
- **Full description**:

> ASTRA is a Vedic astrology and tarot companion that reads from your own birth chart. Tell your story once, and every answer fits your life.
>
> ✦ GRAHA TRACKER: See where every planet is today, counted from your Moon. Watch a turning sky wheel, see your current dasha, and see which periods ahead the tradition calls supportive, mixed or demanding.
>
> ✦ YOUR BIRTH CHART: A North Indian chart (Rasi) with Lagna, Moon sign and birth nakshatra, calculated on your phone (Lahiri ayanamsha, tested against Swiss Ephemeris reference data). If you don't know your exact birth time, ASTRA tells you what's still certain.
>
> ✦ TODAY: A short note on your day from the Moon's position and today's panchang, plus a daily tarot card and a daily guidance card.
>
> ✦ CLASSIC TAROT: The full 78-card deck in its original 1909 colours. Shuffle, fan the deck, and choose your own cards for a one-card or three-card reading.
>
> ✦ ORACLE DECKS: Yes / No, Guidance, Vibe and 27 Nakshatra cards, for quick questions and daily reflection. They work offline.
>
> ✦ CROSS-READING: Your chart and your cards read side by side: where they meet, where they differ, and questions to sit with.
>
> ✦ ASK ASTRA: Ask about your chart, a period or a day. Answers are grounded in your calculated chart and, if you allow it, your story. Every answer shows what it drew on.
>
> ✦ MY STORY (optional): Your life stage, what's on your mind and the events that mattered, so you never repeat yourself. You can turn it off or delete it at any time.
>
> PRIVATE BY DESIGN
> • No account needed to start. Birth details stay on your phone.
> • No ads, nothing sold.
> • Delete everything from inside the app in one step.
>
> ASTRA describes what astrological and tarot traditions say. It does not predict events and is not medical, legal, financial or mental-health advice. For adults 18+.
>
> English, Hindi and Hinglish answers.

- **Category**: Lifestyle. **Tags**: Astrology, Tarot, Spirituality, if offered.
- **Graphics** (in `docs/play/`): `icon-512.png`, `feature-1024x500.png`, `phone-01…08.png` (1080×1920), and `tablet-01…04.png` (2560×1800; upload them for both 7-inch and 10-inch tablets). The screenshots come from the CI emulator and use synthetic test data only.

## 6. Closed test, then production (you; Play rule for new personal accounts)

New personal developer accounts must run a **closed test with at least 12 testers, opted in continuously for 14 days**, before Play lets you apply for production. Testers who leave and rejoin restart the count.

1. Testing → **Closed testing** → create a track and upload the same .aab. Add testers by email list or Google Group (12 or more; 15–20 is safer).
2. Testers join through the opt-in link and install from Play. Anyone with an older sideloaded ASTRA must **uninstall it first**, because the signatures differ.
3. Gather feedback in the app (Me → Send feedback) and in a WhatsApp group. Play asks you to summarise it.
4. After 14 days: Dashboard → **Apply for production**. Review takes up to about 7 days.

## 7. Turn on App Check enforcement (after the first Play install works)

1. Firebase → **App Check** → your Android app → **Play Integrity** → Register, using the app signing SHA-256 from step 3. Play Console → App integrity → **Play Integrity API** → link the Firebase Cloud project.
2. Use the app installed from Play for a few days. Cloudflare → Workers → astra-ai → Logs: `ask` events should show `"appcheck":"ok"`.
3. Then set `APPCHECK = "enforce"` in `worker/wrangler.toml` (tell me and I'll do it). From then on, only the genuine Play build can use the AI server. Sideloaded and modified copies are refused.

## Each later release

Bump `versionCode` (always +1) and `versionName` in `app/build.gradle.kts`, add a CHANGELOG entry, push, and upload the new .aab from the GitHub release to the same track.

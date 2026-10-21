# Putting ASTRA on a Play Console testing track

The PRD ships the beta through Google Play's testing tracks instead of sideloaded APKs, so testers install from the Play Store and don't hit "unknown apps" warnings. Every GitHub release now carries an **.aab** file for this (the .apk stays for your own phone).

Checked 21 October 2026. Google changes these screens often, so look for the same words nearby if a button has moved.

## What it needs from you

- A **Google Play developer account**: one-time **US$25** fee (card needed), identity verification with a government ID, and a phone for device verification. Use your app email.
- About an hour for the first setup, then a few minutes per update.

## Rules that matter for a new personal account

- **Internal testing**: up to 100 testers you name by email, no review wait, no minimum period. **Use this for the 5–10 person alpha.**
- **Closed testing**: needed before you can ever go to production. New personal accounts must run a closed test with **at least 12 testers opted in for 14 days in a row**. The 30–50 person beta (M5) can be this closed test, so it doubles as the production requirement.
- Sideloading the GitHub APK still works in India in 2026, but Google plans developer verification for sideloaded apps on all certified devices in 2027, so Play is the long-term route.

## One-time setup

1. Go to **play.google.com/console** → sign in with your app email → **Create account** → **Personal** → pay and verify identity.
2. **Create app**: name **ASTRA**, default language **English (India)**, **App**, **Free**. Tick the declarations.
3. **Set up your app** (the dashboard checklist). Answers ready for each form:
   - **Privacy policy**: `https://astra-ai.app-support-sg.workers.dev/privacy`
   - **App access**: all features available without special access (anonymous start).
   - **Ads**: No ads.
   - **Content rating**: questionnaire → category "Reference / lifestyle"; no violence, sexual content, gambling or user-to-user chat.
   - **Target audience**: **18 and over** only.
   - **News app**: No. **Health app**: No. **Financial features**: None. **Government app**: No.
   - **Data safety**: see the table below.
4. **App signing**: accept **Play App Signing**. The .aab is signed with ASTRA's upload key (the same key as the APK).

## Data safety answers (review before submitting)

Data is encrypted in transit (HTTPS). Users can ask for deletion (Me → Privacy & data → Delete my account).

| Data type (Play's names) | Collected? | Shared? | Purpose | Optional? |
|---|---|---|---|---|
| Personal info → Other info (calculated chart facts from birth details) | Yes | No | App functionality | Required for AI answers |
| App activity → Other user-generated content (My Story, questions, card draws) | Yes | No | App functionality, personalisation | Optional (My Story is opt-in) |
| App activity → App interactions (usage events) | Yes | No | Analytics | Optional (switch in Me) |
| App info and performance → Crash logs (error type and screen only) | Yes | No | Analytics | Optional (same switch) |
| Device or other IDs (anonymous Firebase account ID) | Yes | No | App functionality | Required |
| Personal info → Email address (only if the user backs up with Google) | Yes | No | Account management | Optional |
| Location | No (the birthplace and current city stay on the phone; only calculated facts are sent) | | | |

"Shared" means given to a third party for its own use. Google (Firebase, Gemini) and Cloudflare process data on ASTRA's behalf as service providers, which Play counts as not shared.

## Releasing a build to internal testing

1. Download the **.aab** from the latest GitHub release (e.g. `ASTRA-v0.4.0.aab`).
2. Play Console → **Test and release → Testing → Internal testing** → **Create new release** → upload the .aab → release name `0.4.0` → notes "Alpha: tarot, Cross-Reading, check-ins" → **Save** → **Review release** → **Start rollout**.
3. **Testers** tab → **Create email list** "ASTRA alpha" → add testers' Gmail addresses → **Save** → copy the **opt-in link** and send it to them.
4. Testers open the link on their phone, tap **Accept invite**, then **Download it on Google Play**.

Each new build needs a higher version code; the app's build file already goes up with each version (0.4.0 is version code 3).

## Later: the closed test for the beta (M5)

Same steps under **Closed testing** with a track named "Beta", at least 12 testers who stay opted in for 14 consecutive days. When it ends, **Apply for production** appears on the dashboard. The PRD keeps the public launch for after the decision.

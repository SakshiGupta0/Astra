# Firebase setup for ASTRA (about 15 minutes, one time)

## 1. Create the project
1. Go to **console.firebase.google.com** → **Create a project** → name it **ASTRA**. Google Analytics: on, default account.
2. Note the **Project ID** shown under the name (for example `astra-1a2b3`). Send it to Claude.

## 2. Turn on the Blaze plan and a budget
1. Bottom-left, click **Spark** → **Upgrade** → **Blaze**, then link or create a billing account with your card.
2. When asked for a budget, set **₹500**. (Or later: Google Cloud console → Billing → Budgets & alerts → create a ₹500 budget with alerts at 50% and 90%.)
   Expected bill at beta volume: ₹0. The budget only emails you; it does not stop services.

## 3. Sign-in
**Build → Authentication → Get started**, then enable **Anonymous** and **Google** (pick your email as the support email).

## 4. Database
**Build → Firestore Database → Create database** → location **asia-south1 (Mumbai)** → **Start in production mode**.
(ASTRA's security rules are deployed from the repo, so production mode is right.)

## 5. Register the Android app
**Project settings (gear) → General → Your apps → Android icon**:
- Package name: `app.astra`
- App nickname: `ASTRA`
- SHA-1: `F4:1A:F3:2C:51:96:45:F8:60:96:27:AC:59:24:18:D0:E3:FC:5A:07`

Click **Register app**, download **google-services.json**, and attach it in the chat with Claude.
It identifies the app to Firebase and is not a password, so it's fine in the private repo.

## 6. Let GitHub deploy the AI server
1. Open **console.cloud.google.com** with the ASTRA project selected → **IAM & Admin → Service accounts → Create service account**.
   Name: `github-deploy`. Grant these roles: **Editor**, **Secret Manager Admin**, **Service Account User**. Done.
2. Open the new account → **Keys → Add key → Create new key → JSON**. A file downloads.
3. In the GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**
   Name `FIREBASE_SERVICE_ACCOUNT`, value: paste the whole contents of that JSON file.
   **Do not send this file to Claude or anyone.** It is a password for your Google Cloud project.
4. `GEMINI_API_KEY` is already a repository secret; the deploy copies it into Firebase's secret store.

Then tell Claude "Firebase done". Claude deploys the database rules and the AI server from GitHub Actions and turns on sign-in and Ask ASTRA in the app.

# Setting up sign-in and the AI server (free, no card)

ASTRA uses **Firebase on the free Spark plan** for sign-in, the user's private database space and backup,
and a **Cloudflare Worker on the free plan** for the AI server that holds the Gemini key.

## Firebase (project `astra-d00af`)
1. **Authentication → Get started** → enable **Anonymous** and **Google** (support email: yours).
2. **Firestore Database → Create database** → location **asia-south1 (Mumbai)** → **production mode**.
3. **Firestore Database → Rules** → replace everything with the contents of `firestore.rules` in this repo → **Publish**.
   (Do this again whenever `firestore.rules` changes. Optional: set the `FIREBASE_SERVICE_ACCOUNT` secret and GitHub deploys rules for you.)
4. **Project settings → Your apps → Android**: package `app.astra`, SHA-1 `F4:1A:F3:2C:51:96:45:F8:60:96:27:AC:59:24:18:D0:E3:FC:5A:07`
   → download **google-services.json** → it goes in `app/google-services.json` (not a password).

## Cloudflare
1. Sign up at **dash.cloudflare.com**. Open **Workers & Pages** once and pick a workers.dev subdomain if asked.
2. Copy the **Account ID**.
3. **My Profile → API Tokens → Create Token → "Edit Cloudflare Workers"** template, add **Account → D1 → Edit**, all zones → create, copy.
4. GitHub → **Settings → Secrets and variables → Actions**: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`. (`GEMINI_API_KEY` is already there.)

Then run **Actions → AI server (Cloudflare) → Run workflow** (or push). It creates the usage database, deploys
`worker/`, stores the Gemini key on Cloudflare, checks `/health`, and writes the server address to `ai-endpoint.txt`.
The next app build picks it up and Ask ASTRA switches on.

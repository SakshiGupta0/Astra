import { test, before } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import { verifyIdToken, verifyAppCheck, resetKeyCache } from "../src/auth.js";
import { decodeFields } from "../src/firestore.js";
import { workerStore } from "../src/store.js";
import worker from "../src/index.js";

const PROJECT = "astra-d00af";
let keyPair, jwk;
const b64url = (bytes) => Buffer.from(bytes).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
async function sign(claims, kid = "k1") {
  const h = b64url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", kid, typ: "JWT" })));
  const p = b64url(new TextEncoder().encode(JSON.stringify(claims)));
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", keyPair.privateKey, new TextEncoder().encode(`${h}.${p}`));
  return `${h}.${p}.${b64url(new Uint8Array(sig))}`;
}
const NOW = Date.parse("2026-10-21T08:00:00Z");
const claims = (o = {}) => ({ aud: PROJECT, iss: `https://securetoken.google.com/${PROJECT}`, sub: "user1", iat: NOW / 1000 - 60, exp: NOW / 1000 + 3000, ...o });
const jwksFetch = async () => new Response(JSON.stringify({ keys: [jwk] }), { headers: { "cache-control": "max-age=600" } });

before(async () => {
  keyPair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  jwk = { ...(await crypto.subtle.exportKey("jwk", keyPair.publicKey)), kid: "k1", alg: "RS256", use: "sig" };
});

test("valid Firebase token gives the uid", async () => {
  resetKeyCache();
  assert.equal(await verifyIdToken(await sign(claims()), PROJECT, { fetchImpl: jwksFetch, now: NOW }), "user1");
});

test("tokens for another project, expired, tampered or unknown key are refused", async () => {
  resetKeyCache();
  const o = { fetchImpl: jwksFetch, now: NOW };
  await assert.rejects(verifyIdToken(await sign(claims({ aud: "other" })), PROJECT, o), /wrong project/);
  await assert.rejects(verifyIdToken(await sign(claims({ exp: NOW / 1000 - 3600 })), PROJECT, o), /expired/);
  const t = await sign(claims()); const parts = t.split(".");
  const forged = b64url(new TextEncoder().encode(JSON.stringify(claims({ sub: "attacker" }))));
  await assert.rejects(verifyIdToken(`${parts[0]}.${forged}.${parts[2]}`, PROJECT, o), /bad signature/);
  await assert.rejects(verifyIdToken(await sign(claims(), "nope"), PROJECT, o), /unknown key/);
  await assert.rejects(verifyIdToken("garbage", PROJECT, o), /malformed/);
});

test("Firestore REST values decode", () => {
  const d = decodeFields({ consent: { booleanValue: true }, json: { stringValue: "{}" }, items: { arrayValue: { values: [{ mapValue: { fields: { id: { stringValue: "thread.a" }, kind: { stringValue: "thread" } } } }] } }, n: { integerValue: "3" } });
  assert.deepEqual(d, { consent: true, json: "{}", items: [{ id: "thread.a", kind: "thread" }], n: 3 });
});

/** Minimal D1 stand-in backed by Node's SQLite. */
function fakeD1() {
  const db = new DatabaseSync(":memory:");
  db.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  const stmt = (sql) => ({
    _sql: sql, _args: [],
    bind(...a) { this._args = a; return this; },
    async first() { return db.prepare(this._sql).get(...this._args) ?? null; },
    async run() { db.prepare(this._sql).run(...this._args); return { success: true }; },
  });
  return { prepare: stmt, async batch(list) { for (const s of list) await s.run(); }, _db: db };
}

test("D1 counters: atomic cap, refund, spend", async () => {
  const db = fakeD1();
  const s = workerStore({ db, projectId: PROJECT, idToken: "t", fetchImpl: async () => new Response("", { status: 404 }) });
  for (let i = 1; i <= 3; i++) assert.deepEqual(await s.reserve("u", "2026-10-21", "ask", 3), { ok: true, used: i });
  assert.deepEqual(await s.reserve("u", "2026-10-21", "ask", 3), { ok: false, used: 3 });
  await s.refund("u", "2026-10-21", "ask");
  assert.equal((await s.reserve("u", "2026-10-21", "ask", 3)).ok, true);
  await s.record("u", "2026-10-21", { model: "m", inTok: 1000, outTok: 200, usd: 0.0005 });
  await s.record("v", "2026-10-21", { model: "m", inTok: 1000, outTok: 200, usd: 0.0005 });
  assert.ok(Math.abs((await s.globalSpend("2026-10-21")) - 0.001) < 1e-9);
  assert.equal(await s.globalSpend("2026-10-22"), 0);
  assert.equal(await s.getFacts("u"), null);
});

test("end to end: signed-in user asks, Firestore read with their token, Gemini answers", async () => {
  resetKeyCache();
  const token = await sign(claims({ iat: Date.now() / 1000 - 60, exp: Date.now() / 1000 + 3000 }));
  const facts = { language: "en", birth_time_status: "known", lagna: "Kanya", placements: [{ graha: "Moon", sign: "Vrishchika", nakshatra: "Anuradha" }], dasha_now: { maha: "Mercury", antar: "Venus", antar_end: "2028-06-18" } };
  const seen = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url, init = {}) => {
    url = String(url); seen.push(url);
    if (url.includes("securetoken")) return jwksFetch();
    if (url.includes("firestore.googleapis.com")) {
      assert.equal(init.headers.authorization, `Bearer ${token}`);
      if (url.endsWith("users/user1/facts/current")) return new Response(JSON.stringify({ fields: { json: { stringValue: JSON.stringify(facts) } } }));
      if (url.endsWith("users/user1/story/items")) return new Response(JSON.stringify({ fields: { consent: { booleanValue: true }, items: { arrayValue: { values: [{ mapValue: { fields: { id: { stringValue: "about.now" }, kind: { stringValue: "about" }, text: { stringValue: "Going on right now: job offers" } } } }] } } } }));
      return new Response("", { status: 404 });
    }
    if (url.includes("generativelanguage")) {
      assert.match(JSON.parse(init.body).contents[0].parts[0].text, /job offers/);
      return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "Your chart shows the Mercury mahadasha with the Venus sub-period until June 2028 and your Moon in Vrishchika in Anuradha. In Vedic tradition this is a time for steady effort. The chart cannot tell when or whether a specific event will happen. What is one step in your control this week?" }] }, finishReason: "STOP" }], usageMetadata: { promptTokenCount: 1500, candidatesTokenCount: 120 } }));
    }
    throw new Error("unexpected fetch " + url);
  };
  try {
    const env = { FIREBASE_PROJECT_ID: PROJECT, ASTRA_MODEL: "gemini-3.1-flash-lite", GEMINI_API_KEY: "k", DB: fakeD1() };
    const ask = (body, tok = token) => worker.fetch(new Request("https://x/ask", { method: "POST", headers: { authorization: `Bearer ${tok}`, "content-type": "application/json" }, body: JSON.stringify(body) }), env);
    const r = await ask({ mode: "ask", question: "Is this a supportive time for my job?", storyIds: ["about.now"] });
    assert.equal(r.status, 200);
    const j = await r.json();
    assert.equal(j.safety, "ok"); assert.deepEqual(j.storyUsed, ["about.now"]); assert.equal(j.remaining, 14);
    const crisis = await (await ask({ question: "I don't want to live anymore" })).json();
    assert.equal(crisis.safety, "crisis"); assert.match(crisis.answer, /14416/);
    const bad = await ask({ question: "hello there" }, "not-a-token");
    assert.equal(bad.status, 401);
    const health = await worker.fetch(new Request("https://x/health"), env);
    assert.equal((await health.json()).ok, true);
  } finally { globalThis.fetch = realFetch; }
});

// ---- M4: events, feedback, deletion, privacy, draws ----
import { cleanEvent } from "../src/events.js";
import { mdToHtml } from "../src/md.js";
import PRIVACY from "../src/privacy-text.js";

test("events: only allowed names, keys and short values", () => {
  const now = Date.parse("2026-10-21T08:00:00Z");
  assert.deepEqual(cleanEvent({ name: "tracker_viewed", at: "2026-10-21T07:00:00Z", props: { tab: "timeline", from_push: true } }, now),
    { name: "tracker_viewed", at: "2026-10-21T07:00:00.000Z", props: { tab: "timeline", from_push: true } });
  assert.equal(cleanEvent({ name: "question_text", props: {} }, now), null);
  assert.equal(cleanEvent({ name: "answer_completed", props: { question: "will I marry" } }, now), null);
  assert.equal(cleanEvent({ name: "app_error", props: { kind: "x".repeat(80) } }, now), null);
  assert.equal(cleanEvent({ name: "app_error", props: { kind: "I am sad <script>" } }, now), null);
  assert.equal(cleanEvent({ name: "today_viewed", at: "2020-01-01T00:00:00Z" }, now).at, new Date(now).toISOString());
});

test("events, feedback and delete-me end to end", async () => {
  resetKeyCache();
  const token = await sign(claims({ iat: Date.now() / 1000 - 60, exp: Date.now() / 1000 + 3000 }));
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url) => { if (String(url).includes("securetoken")) return jwksFetch(); throw new Error("unexpected " + url); };
  try {
    const db = fakeD1();
    const env = { FIREBASE_PROJECT_ID: PROJECT, DB: db };
    const post = (path, body, tok = token) => worker.fetch(new Request("https://x" + path, { method: "POST", headers: { authorization: `Bearer ${tok}` }, body: JSON.stringify(body) }), env);
    const ev = await (await post("/events", { app: "0.4.0", events: [{ name: "app_open", props: { from_push: false } }, { name: "nope" }, { name: "story_consent_changed", props: { state: "on", offer_point: "onboarding" } }] })).json();
    assert.equal(ev.stored, 2);
    assert.equal(db._db.prepare("SELECT COUNT(*) n FROM events WHERE uid='user1'").get().n, 2);
    assert.equal((await post("/feedback", { text: "Love the tracker", app: "0.4.0" })).status, 200);
    assert.equal((await post("/feedback", { text: "" })).status, 400);
    assert.equal((await post("/feedback", { kind: "report", reason: "frightening", mode: "tarot", text: "", answer: "Scary answer text" })).status, 200);
    const rep = db._db.prepare("SELECT text FROM feedback WHERE uid='user1' AND text LIKE '[REPORT%'").get();
    assert.match(rep.text, /^\[REPORT frightening · tarot\] \n---\nScary answer text$/);
    assert.equal((await post("/feedback", { kind: "report", reason: "x", answer: "" })).status, 400);
    assert.equal((await post("/events", { events: [] }, "bad")).status, 401);
    db._db.prepare("INSERT INTO usage (uid, day, mode, used) VALUES ('user1','2026-10-21','ask',3)").run();
    db._db.prepare("INSERT INTO usage (uid, day, mode, used) VALUES ('other','2026-10-21','ask',3)").run();
    assert.equal((await post("/delete-me", {})).status, 200);
    for (const t of ["usage", "events", "feedback", "user_cost"]) assert.equal(db._db.prepare(`SELECT COUNT(*) n FROM ${t} WHERE uid='user1'`).get().n, 0, t);
    assert.equal(db._db.prepare("SELECT COUNT(*) n FROM usage WHERE uid='other'").get().n, 1);
  } finally { globalThis.fetch = realFetch; }
});

test("privacy page is public, matches the app's notice, and fills in the contact", async () => {
  const md = readFileSync(new URL("../../docs/privacy/PRIVACY.md", import.meta.url), "utf8");
  assert.equal(PRIVACY, md, "run node tools/gen-privacy.mjs");
  const r = await worker.fetch(new Request("https://x/privacy"), { CONTACT_EMAIL: "hello@example.com" });
  const html = await r.text();
  assert.equal(r.status, 200); assert.match(html, /hello@example\.com/); assert.match(html, /<h2>Your choices and rights<\/h2>/);
  assert.doesNotMatch(html, /\{\{CONTACT\}\}/);
  assert.equal(mdToHtml("Hi <b>x</b> **y**"), "<p>Hi &lt;b&gt;x&lt;/b&gt; <strong>y</strong></p>");
});

test("draws are read from the user's own Firestore space", async () => {
  const facts = { cards: [{ position: "Your card", card: "The Star", orientation: "upright" }] };
  const s = workerStore({ db: fakeD1(), projectId: PROJECT, idToken: "t", fetchImpl: async (url) =>
    String(url).endsWith("users/u/draws/abc123") ? new Response(JSON.stringify({ fields: { facts: { stringValue: JSON.stringify(facts) } } })) : new Response("", { status: 404 }) });
  assert.deepEqual(await s.getDraw("u", "abc123"), { facts });
  assert.equal(await s.getDraw("u", "../facts/current"), null);
  assert.equal(await s.getDraw("u", "zzz"), null);
});

test("public account-deletion page", async () => {
  const r = await worker.fetch(new Request("https://x/delete-account"), { CONTACT_EMAIL: "help@example.com" });
  assert.equal(r.status, 200);
  const html = await r.text();
  assert.match(html, /Delete your ASTRA account/);
  assert.match(html, /help@example\.com/);
  assert.match(html, /Privacy &amp; data/);
});

test("App Check tokens: genuine accepted; wrong project, expired, forged or missing refused; enforce mode blocks", async () => {
  resetKeyCache();
  const NUM = "359300157269";
  const ac = (o = {}) => ({ iss: `https://firebaseappcheck.googleapis.com/${NUM}`, aud: [`projects/${NUM}`, `projects/${PROJECT}`], sub: "1:359300157269:android:abc", exp: NOW / 1000 + 3600, iat: NOW / 1000 - 60, ...o });
  const o = { fetchImpl: jwksFetch, now: NOW };
  assert.equal(await verifyAppCheck(await sign(ac()), NUM, o), "ok");
  assert.equal(await verifyAppCheck(await sign(ac({ aud: ["projects/999"] })), NUM, o), "invalid");
  assert.equal(await verifyAppCheck(await sign(ac({ iss: "https://firebaseappcheck.googleapis.com/999" })), NUM, o), "invalid");
  assert.equal(await verifyAppCheck(await sign(ac({ exp: NOW / 1000 - 10 })), NUM, o), "invalid");
  const t = (await sign(ac())).split(".");
  assert.equal(await verifyAppCheck(`${t[0]}.${t[1]}x.${t[2]}`, NUM, o), "invalid");
  assert.equal(await verifyAppCheck("", NUM, o), "missing");
  // Enforce mode: a signed-in request without App Check is refused before any work is done.
  resetKeyCache();
  const realFetch = globalThis.fetch;
  globalThis.fetch = async () => jwksFetch();
  try {
    const token = await sign(claims({ iat: Date.now() / 1000 - 60, exp: Date.now() / 1000 + 3000 }));
    const env = { FIREBASE_PROJECT_ID: PROJECT, FIREBASE_PROJECT_NUMBER: NUM, APPCHECK: "enforce", DB: fakeD1() };
    const r = await worker.fetch(new Request("https://x/feedback", { method: "POST", headers: { authorization: `Bearer ${token}` }, body: JSON.stringify({ text: "hello" }) }), env);
    assert.equal(r.status, 403);
    const good = await sign(ac({ exp: Date.now() / 1000 + 3600 }));
    const r2 = await worker.fetch(new Request("https://x/feedback", { method: "POST", headers: { authorization: `Bearer ${token}`, "x-firebase-appcheck": good }, body: JSON.stringify({ text: "hello" }) }), env);
    assert.equal(r2.status, 200);
  } finally { globalThis.fetch = realFetch; }
});

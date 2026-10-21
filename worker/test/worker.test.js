import { test, before } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import { verifyIdToken, resetKeyCache } from "../src/auth.js";
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

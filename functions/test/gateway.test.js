import { test } from "node:test";
import assert from "node:assert/strict";
import { handle, istDate, nextIstMidnight, isCrisis, GatewayError, LIMITS, GLOBAL_USD_PER_DAY } from "../lib/gateway.js";

const FACTS = {
  language: "en", birth_time_status: "known", lagna: "Kanya", lagna_status: "exact",
  placements: [{ graha: "Moon", sign: "Vrishchika", nakshatra: "Anuradha", house_from_moon: 1 }],
  dasha_now: { maha: "Mercury", antar: "Venus", antar_end: "2028-06-18" },
  tracker: { tone_now: "mixed", factors: ["Jupiter 9th from your Moon — traditionally supportive"] },
};
const GOOD = "Your chart shows the Mercury mahadasha with the Venus sub-period until June 2028, and your Moon in Vrishchika in Anuradha. In Vedic tradition this is read as a time for steady effort. The chart cannot tell when or whether a specific event will happen. Something to reflect on: what is one step in your control this week?";

function fakeStore({ facts = FACTS, story = null, spend = 0 } = {}) {
  const usage = {};
  const recorded = [];
  return {
    usage, recorded,
    getFacts: async () => facts,
    getStory: async () => story,
    reserve: async (uid, day, mode, limit) => { const k = `${uid}/${day}/${mode}`; const used = usage[k] ?? 0; if (used >= limit) return { ok: false, used }; usage[k] = used + 1; return { ok: true, used: used + 1 }; },
    refund: async (uid, day, mode) => { const k = `${uid}/${day}/${mode}`; usage[k] = (usage[k] ?? 1) - 1; },
    record: async (uid, day, u) => recorded.push(u),
    globalSpend: async () => spend,
  };
}
const llmReturning = (...texts) => { let i = 0; const calls = []; const f = async (p) => { calls.push(p); const t = texts[Math.min(i++, texts.length - 1)]; if (t instanceof Error) throw t; return { text: t, inTok: 1500, outTok: 300 }; }; f.calls = calls; return f; };
const NOW = new Date("2026-10-21T05:00:00Z");
const deps = (store, llm) => ({ store, llm, now: () => NOW, model: "gemini-3.1-flash-lite" });

test("IST day and midnight", () => {
  assert.equal(istDate(new Date("2026-10-20T18:29:00Z")), "2026-10-20");
  assert.equal(istDate(new Date("2026-10-20T18:31:00Z")), "2026-10-21");
  assert.equal(nextIstMidnight(NOW).toISOString(), "2026-10-21T18:30:00.000Z");
});

test("answers and counts usage", async () => {
  const store = fakeStore(); const llm = llmReturning(GOOD);
  const r = await handle({ uid: "u1", question: "What is my current period about?" }, deps(store, llm));
  assert.equal(r.answer, GOOD); assert.equal(r.safety, "ok"); assert.equal(r.remaining, LIMITS.ask - 1);
  assert.equal(store.recorded.length, 1); assert.ok(store.recorded[0].usd > 0);
  assert.match(llm.calls[0].user, /"story": \{\n  "enabled": false/);
});

test("daily cap of 15", async () => {
  const store = fakeStore(); const llm = llmReturning(GOOD);
  for (let i = 0; i < LIMITS.ask; i++) await handle({ uid: "u1", question: "Tell me about my Moon" }, deps(store, llm));
  await assert.rejects(handle({ uid: "u1", question: "One more" }, deps(store, llm)), (e) => e instanceof GatewayError && e.code === "resource-exhausted" && e.details.reason === "user");
  // another user is unaffected
  await handle({ uid: "u2", question: "Tell me about my Moon" }, deps(store, llm));
});

test("global spend guard pauses AI", async () => {
  await assert.rejects(handle({ uid: "u1", question: "hello there" }, deps(fakeStore({ spend: GLOBAL_USD_PER_DAY }), llmReturning(GOOD))),
    (e) => e.code === "resource-exhausted" && e.details.reason === "global");
});

test("crisis gets care and helpline, no model call, no cap used", async () => {
  const store = fakeStore(); const llm = llmReturning(GOOD);
  for (const q of ["I don't want to live anymore, will Sade Sati end?", "mujhe marna chahti hoon", "मैं आत्महत्या के बारे में सोच रही हूँ"]) {
    const r = await handle({ uid: "u1", question: q }, deps(store, llm));
    assert.equal(r.safety, "crisis"); assert.match(r.answer, /14416/);
  }
  assert.equal(llm.calls.length, 0); assert.equal(Object.keys(store.usage).length, 0);
  assert.equal(isCrisis("When will my bad time end?"), false);
});

test("story only with consent and only requested items", async () => {
  const story = { consent: true, items: [{ id: "about.now", kind: "about", text: "Going on right now: job offers" }, { id: "thread.a", kind: "thread", text: "Open question: Job switch" }, { id: "thread.b", kind: "thread", text: "Open question: Parents want me to meet a match" }] };
  const llm = llmReturning(GOOD);
  const r = await handle({ uid: "u1", question: "Is this a supportive time for my job?", storyIds: ["about.now", "thread.a"] }, deps(fakeStore({ story }), llm));
  assert.deepEqual(r.storyUsed, ["about.now", "thread.a"]);
  assert.match(llm.calls[0].user, /Job switch/); assert.doesNotMatch(llm.calls[0].user, /meet a match/);
  const llm2 = llmReturning(GOOD);
  const r2 = await handle({ uid: "u1", question: "job?", storyIds: ["thread.a"] }, deps(fakeStore({ story: { ...story, consent: false } }), llm2));
  assert.deepEqual(r2.storyUsed, []); assert.doesNotMatch(llm2.calls[0].user, /Job switch/);
});

test("invented facts trigger one retry, then a safe fallback", async () => {
  const bad = GOOD.replace("Vrishchika", "Mesha") + " You will marry in 2029.";
  const llm = llmReturning(bad, GOOD);
  const r = await handle({ uid: "u1", question: "When will I marry?" }, deps(fakeStore(), llm));
  assert.equal(r.answer, GOOD); assert.equal(llm.calls.length, 2); assert.match(llm.calls[1].user, /broke these rules/);
  const r2 = await handle({ uid: "u1", question: "When will I marry?" }, deps(fakeStore(), llmReturning(bad, bad)));
  assert.equal(r2.safety, "fallback"); assert.doesNotMatch(r2.answer, /2029/);
});

test("model failure refunds the question", async () => {
  const store = fakeStore();
  await assert.rejects(handle({ uid: "u1", question: "hello there" }, deps(store, llmReturning(new Error("boom")))), (e) => e.code === "unavailable");
  assert.equal(store.usage["u1/2026-10-21/ask"], 0);
});

test("missing chart and bad input are explained", async () => {
  await assert.rejects(handle({ uid: "u1", question: "hi there" }, deps(fakeStore({ facts: null }), llmReturning(GOOD))), (e) => e.code === "failed-precondition");
  await assert.rejects(handle({ uid: "u1", question: "x".repeat(501) }, deps(fakeStore(), llmReturning(GOOD))), (e) => e.code === "invalid-argument");
});

test("today line: one per day, uses open threads", async () => {
  const story = { consent: true, items: [{ id: "thread.a", kind: "thread", text: "Open question: Job switch" }, { id: "digest.x", kind: "digest", text: "Earlier reading" }] };
  const store = fakeStore({ story }); const llm = llmReturning("The Moon moves through your 7th today; a calm day to weigh the job switch with care.");
  const r = await handle({ uid: "u1", mode: "today_line" }, deps(store, llm));
  assert.deepEqual(r.storyUsed, ["thread.a"]);
  await assert.rejects(handle({ uid: "u1", mode: "today_line" }, deps(store, llm)), (e) => e.code === "resource-exhausted");
});

// ---- Tarot and Cross-Reading (M4) ----
const DRAW = { facts: { deck_version: "astra-deck-1", spread: "Three cards", question: "Should I take the offer?", cards: [
  { position: "What's behind it", card: "Eight of Pentacles", orientation: "upright", keywords: "craft, diligence, skill-building" },
  { position: "Where you are", card: "The Hermit", orientation: "reversed", keywords: "isolation, or avoiding needed time alone" },
  { position: "What can help", card: "Ace of Cups", orientation: "upright", keywords: "new feelings, openness, emotional beginnings" }] } };
const TAROT_OK = "In tarot, the Eight of Pentacles in the first position speaks of patient craft behind this question. The Hermit reversed, where you are, can point to pulling away from the quiet thinking a decision needs. The Ace of Cups, what can help, is an opening of feeling. Something to reflect on: what does your heart say when you imagine saying yes?";
const CROSS_OK = "Your chart: You are in the Mercury mahadasha with the Venus sub-period until June 2028, and the tone is mixed.\nYour cards: In tarot, the Eight of Pentacles shows steady craft, The Hermit reversed asks for quiet reflection, and the Ace of Cups opens feeling.\nWhere they meet: Both point to patient, skill-based effort.\nWhere they differ: The chart describes a long period; the cards speak to how you feel now.\nQuestions to sit with: What would steady effort look like this month? What does your heart want?";
const withDraw = (o = {}) => ({ ...fakeStore(o), getDraw: async (uid, id) => (id === "d1" ? DRAW : null) });

test("tarot reads the saved draw, counts as a message, and gets no chart facts", async () => {
  const store = withDraw(); const llm = llmReturning(TAROT_OK);
  const r = await handle({ uid: "u1", mode: "tarot", drawId: "d1" }, deps(store, llm));
  assert.equal(r.safety, "ok"); assert.equal(r.mode, "tarot"); assert.equal(r.remaining, LIMITS.ask - 1);
  const user = llm.calls[0].user;
  assert.match(user, /The Hermit/); assert.match(user, /Should I take the offer\?/);
  assert.doesNotMatch(user, /Vrishchika|mahadasha|placements/);
  assert.match(llm.calls[0].system, /TAROT READING/);
});

test("tarot refuses a draw that isn't saved, or cards outside the deck", async () => {
  await assert.rejects(handle({ uid: "u1", mode: "tarot", drawId: "nope" }, deps(withDraw(), llmReturning(TAROT_OK))), (e) => e.code === "failed-precondition");
  const fake = { ...fakeStore(), getDraw: async () => ({ facts: { cards: [{ position: "x", card: "The Unicorn", orientation: "upright" }] } }) };
  await assert.rejects(handle({ uid: "u1", mode: "tarot", drawId: "d1" }, deps(fake, llmReturning(TAROT_OK))), (e) => e.code === "failed-precondition");
});

test("an answer naming a card that wasn't drawn is retried", async () => {
  const llm = llmReturning(TAROT_OK + " The Tower also suggests upheaval.", TAROT_OK);
  const r = await handle({ uid: "u1", mode: "tarot", drawId: "d1" }, deps(withDraw(), llm));
  assert.equal(r.safety, "ok"); assert.equal(llm.calls.length, 2); assert.match(llm.calls[1].user, /card not drawn: The Tower/);
});

test("'the Moon' as a planet in a Cross-Reading is not mistaken for a card", async () => {
  const text = CROSS_OK.replace("and the tone is mixed.", "and the Moon is in Vrishchika; the tone is mixed.");
  const r = await handle({ uid: "u1", mode: "cross", drawId: "d1" }, deps(withDraw(), llmReturning(text)));
  assert.equal(r.safety, "ok");
});

test("Cross-Reading: five sections, own cap of 2, chart and cards both in DATA", async () => {
  const store = withDraw(); const llm = llmReturning(CROSS_OK);
  const r1 = await handle({ uid: "u1", mode: "cross", drawId: "d1" }, deps(store, llm));
  assert.equal(r1.safety, "ok"); assert.equal(r1.remaining, 1);
  assert.match(llm.calls[0].user, /Vrishchika/); assert.match(llm.calls[0].user, /Ace of Cups/);
  assert.match(llm.calls[0].system, /Where they differ:/);
  await handle({ uid: "u1", mode: "cross", drawId: "d1" }, deps(store, llm));
  await assert.rejects(handle({ uid: "u1", mode: "cross", drawId: "d1" }, deps(store, llm)), (e) => e.code === "resource-exhausted" && /Cross-Readings/.test(e.message));
  // the 15 questions are untouched
  const a = await handle({ uid: "u1", question: "Tell me about my Moon" }, deps(store, llmReturning(GOOD)));
  assert.equal(a.remaining, LIMITS.ask - 1);
});

test("Cross-Reading missing a section falls back and doesn't use up a reading", async () => {
  const store = withDraw(); const broken = CROSS_OK.replace("Where they differ:", "Differences:");
  const r = await handle({ uid: "u1", mode: "cross", drawId: "d1" }, deps(store, llmReturning(broken, broken)));
  assert.equal(r.safety, "fallback"); assert.equal(r.remaining, 2);
});

test("Cross-Reading needs exactly three cards", async () => {
  const one = { ...fakeStore(), getDraw: async () => ({ facts: { cards: [DRAW.facts.cards[0]] } }) };
  await assert.rejects(handle({ uid: "u1", mode: "cross", drawId: "d1" }, deps(one, llmReturning(CROSS_OK))), (e) => e.code === "failed-precondition");
});

test("crisis words in a tarot question get care, not a reading", async () => {
  const llm = llmReturning(TAROT_OK);
  const r = await handle({ uid: "u1", mode: "tarot", drawId: "d1", question: "I want to end my life, what do the cards say" }, deps(withDraw(), llm));
  assert.equal(r.safety, "crisis"); assert.equal(llm.calls.length, 0);
});

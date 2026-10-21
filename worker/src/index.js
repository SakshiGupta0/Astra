// ASTRA AI server on Cloudflare Workers. The Gemini key never leaves the server.
//   POST /ask        AI answers (ask, today_line, tarot, cross) with a Firebase ID token
//   POST /events     content-free usage events
//   POST /feedback   feedback text from Me → Send feedback
//   POST /delete-me  removes the caller's counters, events and feedback
//   GET  /privacy    the privacy notice (public)
//   GET  /health
import { handle, GatewayError, istDate } from "../../functions/lib/gateway.js";
import { callGemini } from "../../functions/lib/gemini.js";
import { verifyIdToken } from "./auth.js";
import { workerStore, workerExtras } from "./store.js";
import { cleanEvent, MAX_BATCH, MAX_PER_DAY } from "./events.js";
import { privacyPage } from "./md.js";
import PRIVACY from "./privacy-text.js";

const STATUS = { "invalid-argument": 400, unauthenticated: 401, "failed-precondition": 412, "resource-exhausted": 429, unavailable: 503, internal: 500 };
const reply = (status, body) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
const fail = (code, message, extra = {}) => reply(STATUS[code] ?? 500, { error: { code, message, ...extra } });
const short = (s, n) => String(s ?? "").replace(/[^\w.\- ]/g, "").slice(0, n);

export default {
  async fetch(request, env, ctx, deps = {}) {
    const url = new URL(request.url);
    const now = deps.now ?? Date.now;
    if (url.pathname === "/health") return reply(200, { ok: true, model: env.ASTRA_MODEL });
    if (url.pathname === "/privacy" && request.method === "GET")
      return new Response(privacyPage(PRIVACY, env.CONTACT_EMAIL || "us using Send feedback in the app (Me → Send feedback)"),
        { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=3600" } });
    if (request.method !== "POST" || !["/ask", "/events", "/feedback", "/delete-me"].includes(url.pathname)) return fail("invalid-argument", "Not found.");

    const started = now();
    const token = (request.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
    let uid;
    try { uid = await verifyIdToken(token, env.FIREBASE_PROJECT_ID, deps.auth ?? {}); }
    catch { return fail("unauthenticated", "Please open ASTRA again to sign in."); }
    let body;
    try { body = await request.json(); } catch { return fail("invalid-argument", "Bad request."); }
    const extras = workerExtras(env.DB);
    const today = istDate(new Date(started));

    if (url.pathname === "/events") {
      const list = (Array.isArray(body.events) ? body.events : []).slice(0, MAX_BATCH).map(e => cleanEvent(e, started)).filter(Boolean);
      const room = MAX_PER_DAY - (await extras.eventsToday(uid, today));
      const kept = list.slice(0, Math.max(0, room));
      await extras.addEvents(uid, short(body.app, 20), kept, istDate);
      return reply(200, { ok: true, stored: kept.length });
    }
    if (url.pathname === "/feedback") {
      const text = String(body.text ?? "").trim().slice(0, 1000);
      if (text.length < 2) return fail("invalid-argument", "Please write a few words.");
      if ((await extras.feedbackToday(uid, today)) >= 10) return fail("resource-exhausted", "Thanks! You've sent a lot of feedback today. Please send more tomorrow.");
      await extras.addFeedback(uid, short(body.app, 20), text, new Date(started).toISOString());
      return reply(200, { ok: true });
    }
    if (url.pathname === "/delete-me") {
      await extras.deleteUser(uid);
      console.log(JSON.stringify({ ev: "user_deleted" }));
      return reply(200, { ok: true });
    }

    const model = env.ASTRA_MODEL || "gemini-3.1-flash-lite";
    try {
      const res = await handle(
        { uid, mode: body.mode, question: body.question, drawId: body.drawId, storyIds: Array.isArray(body.storyIds) ? body.storyIds.map(String) : [] },
        {
          store: deps.store ?? workerStore({ db: env.DB, projectId: env.FIREBASE_PROJECT_ID, idToken: token }),
          llm: deps.llm ?? ((p) => callGemini({ apiKey: env.GEMINI_API_KEY, model, ...p })),
          now: () => new Date(now()),
          model,
        }
      );
      // Content-free log: no question, answer, chart or story text.
      console.log(JSON.stringify({ ev: "ask", mode: res.mode ?? body.mode ?? "ask", safety: res.safety, story_items: res.storyUsed.length, ms: now() - started }));
      return reply(200, res);
    } catch (e) {
      if (e instanceof GatewayError) {
        console.log(JSON.stringify({ ev: "ask_refused", code: e.code, reason: e.details?.reason, ms: now() - started }));
        return fail(e.code, e.message, { resetsAt: e.details?.resetsAt, reason: e.details?.reason });
      }
      console.log(JSON.stringify({ ev: "ask_error", error: String(e?.message ?? e).slice(0, 300) }));
      return fail("internal", "Something went wrong. Please try again.");
    }
  },
};

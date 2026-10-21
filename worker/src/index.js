// ASTRA AI server on Cloudflare Workers. POST /ask with a Firebase ID token. The Gemini key never leaves the server.
import { handle, GatewayError } from "../../functions/lib/gateway.js";
import { callGemini } from "../../functions/lib/gemini.js";
import { verifyIdToken } from "./auth.js";
import { workerStore } from "./store.js";

const STATUS = { "invalid-argument": 400, unauthenticated: 401, "failed-precondition": 412, "resource-exhausted": 429, unavailable: 503, internal: 500 };
const reply = (status, body) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
const fail = (code, message, extra = {}) => reply(STATUS[code] ?? 500, { error: { code, message, ...extra } });

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health") return reply(200, { ok: true, model: env.ASTRA_MODEL });
    if (url.pathname !== "/ask" || request.method !== "POST") return fail("invalid-argument", "Not found.");
    const started = Date.now();
    const token = (request.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
    let uid;
    try { uid = await verifyIdToken(token, env.FIREBASE_PROJECT_ID); }
    catch { return fail("unauthenticated", "Please open ASTRA again to sign in."); }
    let body;
    try { body = await request.json(); } catch { return fail("invalid-argument", "Bad request."); }
    const model = env.ASTRA_MODEL || "gemini-3.1-flash-lite";
    try {
      const res = await handle(
        { uid, mode: body.mode, question: body.question, storyIds: Array.isArray(body.storyIds) ? body.storyIds.map(String) : [] },
        {
          store: workerStore({ db: env.DB, projectId: env.FIREBASE_PROJECT_ID, idToken: token }),
          llm: (p) => callGemini({ apiKey: env.GEMINI_API_KEY, model, ...p }),
          now: () => new Date(),
          model,
        }
      );
      // Content-free log: no question, answer, chart or story text.
      console.log(JSON.stringify({ ev: "ask", mode: body.mode ?? "ask", safety: res.safety, story_items: res.storyUsed.length, ms: Date.now() - started }));
      return reply(200, res);
    } catch (e) {
      if (e instanceof GatewayError) {
        console.log(JSON.stringify({ ev: "ask_refused", code: e.code, reason: e.details?.reason, ms: Date.now() - started }));
        return fail(e.code, e.message, { resetsAt: e.details?.resetsAt, reason: e.details?.reason });
      }
      console.log(JSON.stringify({ ev: "ask_error", error: String(e?.message ?? e).slice(0, 300) }));
      return fail("internal", "Something went wrong. Please try again.");
    }
  },
};

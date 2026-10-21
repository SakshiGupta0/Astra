// ASTRA Cloud Functions (asia-south1). One callable: ask. The Gemini key never leaves the server.
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { handle, GatewayError } from "./lib/gateway.js";
import { callGemini } from "./lib/gemini.js";
import { firestoreStore } from "./lib/store.js";

initializeApp();
const db = getFirestore();
const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");
const MODEL = defineString("ASTRA_MODEL", { default: "gemini-3.1-flash-lite" });

export const ask = onCall(
  { region: "asia-south1", secrets: [GEMINI_API_KEY], timeoutSeconds: 120, memory: "256MiB", maxInstances: 5, concurrency: 20 },
  async (request) => {
    if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Please open ASTRA again to sign in.");
    const model = MODEL.value();
    const started = Date.now();
    try {
      const res = await handle(
        { uid: request.auth.uid, mode: request.data?.mode, question: request.data?.question, storyIds: request.data?.storyIds },
        {
          store: firestoreStore(db),
          llm: (p) => callGemini({ apiKey: GEMINI_API_KEY.value(), model, ...p }),
          now: () => new Date(),
          model,
        }
      );
      // Content-free log: no question, answer, chart or story text.
      logger.info("ask", { mode: request.data?.mode ?? "ask", safety: res.safety, story_items: res.storyUsed.length, ms: Date.now() - started, model: res.model });
      return res;
    } catch (e) {
      if (e instanceof GatewayError) {
        logger.warn("ask_refused", { code: e.code, reason: e.details?.reason, ms: Date.now() - started });
        throw new HttpsError(e.code, e.message, { resetsAt: e.details?.resetsAt, reason: e.details?.reason });
      }
      logger.error("ask_error", { error: String(e?.message ?? e).slice(0, 300) });
      throw new HttpsError("internal", "Something went wrong. Please try again.");
    }
  }
);

// Gateway storage on Cloudflare: usage counters in D1, the user's facts and story from Firestore (read as the user).
import { getUserDoc } from "./firestore.js";

export function workerStore({ db, projectId, idToken, fetchImpl = fetch }) {
  return {
    async getFacts(uid) {
      const d = await getUserDoc(projectId, idToken, `users/${uid}/facts/current`, fetchImpl);
      if (!d?.json) return null;
      try { return JSON.parse(d.json); } catch { return null; }
    },
    async getStory(uid) {
      const d = await getUserDoc(projectId, idToken, `users/${uid}/story/items`, fetchImpl);
      return d ? { consent: d.consent === true, items: d.items ?? [] } : null;
    },
    async getDraw(uid, drawId) {
      if (!/^[A-Za-z0-9_-]{1,40}$/.test(drawId)) return null;
      const d = await getUserDoc(projectId, idToken, `users/${uid}/draws/${drawId}`, fetchImpl);
      if (!d?.facts) return null;
      try { return { facts: JSON.parse(d.facts) }; } catch { return null; }
    },
    async reserve(uid, day, mode, limit) {
      // Atomic: increments only while under the limit; returns no row when the limit is reached.
      const row = await db.prepare(
        "INSERT INTO usage (uid, day, mode, used) VALUES (?1, ?2, ?3, 1) ON CONFLICT (uid, day, mode) DO UPDATE SET used = used + 1 WHERE used < ?4 RETURNING used"
      ).bind(uid, day, mode, limit).first();
      if (row) return { ok: true, used: row.used };
      const cur = await db.prepare("SELECT used FROM usage WHERE uid = ?1 AND day = ?2 AND mode = ?3").bind(uid, day, mode).first();
      return { ok: false, used: cur?.used ?? limit };
    },
    async refund(uid, day, mode) {
      await db.prepare("UPDATE usage SET used = MAX(used - 1, 0) WHERE uid = ?1 AND day = ?2 AND mode = ?3").bind(uid, day, mode).run();
    },
    async record(uid, day, u) {
      await db.batch([
        db.prepare("INSERT INTO user_cost (uid, day, in_tok, out_tok, usd) VALUES (?1, ?2, ?3, ?4, ?5) ON CONFLICT (uid, day) DO UPDATE SET in_tok = in_tok + ?3, out_tok = out_tok + ?4, usd = usd + ?5").bind(uid, day, u.inTok, u.outTok, u.usd),
        db.prepare("INSERT INTO spend (day, usd, calls) VALUES (?1, ?2, 1) ON CONFLICT (day) DO UPDATE SET usd = usd + ?2, calls = calls + 1").bind(day, u.usd),
      ]);
    },
    async globalSpend(day) {
      const r = await db.prepare("SELECT usd FROM spend WHERE day = ?1").bind(day).first();
      return r?.usd ?? 0;
    },
  };
}

/** Usage events, feedback and account deletion (all keyed by the anonymous Firebase uid). */
export function workerExtras(db) {
  return {
    async eventsToday(uid, day) {
      const r = await db.prepare("SELECT COUNT(*) AS n FROM events WHERE uid = ?1 AND day = ?2").bind(uid, day).first();
      return r?.n ?? 0;
    },
    async addEvents(uid, app, list, dayOf) {
      if (!list.length) return;
      await db.batch(list.map(e => db.prepare("INSERT INTO events (uid, at, day, name, props, app) VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
        .bind(uid, e.at, dayOf(new Date(e.at)), e.name, JSON.stringify(e.props), app)));
    },
    async feedbackToday(uid, day) {
      const r = await db.prepare("SELECT COUNT(*) AS n FROM feedback WHERE uid = ?1 AND substr(at, 1, 10) = ?2").bind(uid, day).first();
      return r?.n ?? 0;
    },
    async addFeedback(uid, app, text, at) {
      await db.prepare("INSERT INTO feedback (uid, at, text, app) VALUES (?1, ?2, ?3, ?4)").bind(uid, at, text, app).run();
    },
    async deleteUser(uid) {
      await db.batch(["usage", "user_cost", "events", "feedback"].map(t => db.prepare(`DELETE FROM ${t} WHERE uid = ?1`).bind(uid)));
    },
  };
}

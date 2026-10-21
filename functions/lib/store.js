// Firestore-backed storage for the gateway. Layout (see firestore.rules):
//   users/{uid}/facts/current   { json, calc_version, updated_at }      written by the app
//   users/{uid}/story/items     { consent, items: [{id, kind, text}] }  written by the app
//   users/{uid}/usage/{day}     { ask, today_line, in_tok, out_tok, usd } written here only
//   usage/{day}                 { usd, calls }                          written here only
import { FieldValue } from "firebase-admin/firestore";

export function firestoreStore(db) {
  return {
    async getFacts(uid) {
      const s = await db.doc(`users/${uid}/facts/current`).get();
      if (!s.exists) return null;
      try { return JSON.parse(s.get("json")); } catch { return null; }
    },
    async getStory(uid) {
      const s = await db.doc(`users/${uid}/story/items`).get();
      return s.exists ? { consent: s.get("consent") === true, items: s.get("items") ?? [] } : null;
    },
    async reserve(uid, day, mode, limit) {
      const ref = db.doc(`users/${uid}/usage/${day}`);
      return db.runTransaction(async tx => {
        const snap = await tx.get(ref);
        const used = (snap.exists ? snap.get(mode) : 0) ?? 0;
        if (used >= limit) return { ok: false, used };
        tx.set(ref, { [mode]: used + 1, updated_at: FieldValue.serverTimestamp() }, { merge: true });
        return { ok: true, used: used + 1 };
      });
    },
    async refund(uid, day, mode) {
      await db.doc(`users/${uid}/usage/${day}`).set({ [mode]: FieldValue.increment(-1) }, { merge: true });
    },
    async record(uid, day, u) {
      const batch = db.batch();
      batch.set(db.doc(`users/${uid}/usage/${day}`), { in_tok: FieldValue.increment(u.inTok), out_tok: FieldValue.increment(u.outTok), usd: FieldValue.increment(u.usd) }, { merge: true });
      batch.set(db.doc(`usage/${day}`), { usd: FieldValue.increment(u.usd), calls: FieldValue.increment(1), [`models.${u.model.replace(/\./g, "_")}`]: FieldValue.increment(1) }, { merge: true });
      await batch.commit();
    },
    async globalSpend(day) {
      const s = await db.doc(`usage/${day}`).get();
      return s.exists ? (s.get("usd") ?? 0) : 0;
    },
  };
}

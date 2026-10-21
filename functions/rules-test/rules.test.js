// Firestore security rules tests (run in CI against the Firestore emulator).
import { test, before, after } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, deleteDoc, addDoc, collection, getDocs } from "firebase/firestore";

let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: "astra-rules-test", firestore: { rules: readFileSync("../../firestore.rules", "utf8") } });
});
after(async () => { await env?.cleanup(); });

test("users read and write their own facts and story", async () => {
  const me = env.authenticatedContext("alice").firestore();
  await assertSucceeds(setDoc(doc(me, "users/alice/facts/current"), { json: "{}" }));
  await assertSucceeds(getDoc(doc(me, "users/alice/facts/current")));
  await assertSucceeds(setDoc(doc(me, "users/alice/story/items"), { consent: true, items: [] }));
  await assertSucceeds(setDoc(doc(me, "users/alice"), { language: "en" }));
});

test("no one reads or writes another user's data", async () => {
  const bob = env.authenticatedContext("bob").firestore();
  await assertFails(getDoc(doc(bob, "users/alice/facts/current")));
  await assertFails(setDoc(doc(bob, "users/alice/story/items"), { consent: true }));
  await assertFails(getDoc(doc(bob, "users/alice")));
});

test("signed-out access is denied", async () => {
  const anon = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anon, "users/alice/facts/current")));
});

test("usage counters are read-only for the user and hidden from others", async () => {
  const me = env.authenticatedContext("alice").firestore();
  await assertSucceeds(getDoc(doc(me, "users/alice/usage/2026-10-21")));
  await assertFails(setDoc(doc(me, "users/alice/usage/2026-10-21"), { ask: 0 }));
  await assertFails(getDoc(doc(me, "usage/2026-10-21")));
  await assertFails(setDoc(doc(me, "usage/2026-10-21"), { usd: 0 }));
});

test("card draws and consent records: own only; the owner can list and delete everything (account deletion)", async () => {
  const me = env.authenticatedContext("carol").firestore();
  const bob = env.authenticatedContext("bob").firestore();
  await assertSucceeds(setDoc(doc(me, "users/carol/draws/d1"), { facts: "{}" }));
  await assertSucceeds(addDoc(collection(me, "users/carol/consents"), { purpose: "my_story", granted: true, copy_version: "story-consent-1" }));
  await assertFails(getDoc(doc(bob, "users/carol/draws/d1")));
  await assertFails(getDocs(collection(bob, "users/carol/consents")));
  await assertFails(addDoc(collection(bob, "users/carol/consents"), { granted: false }));
  const list = await assertSucceeds(getDocs(collection(me, "users/carol/consents")));
  for (const d of list.docs) await assertSucceeds(deleteDoc(d.ref));
  await assertSucceeds(deleteDoc(doc(me, "users/carol/draws/d1")));
  await assertSucceeds(deleteDoc(doc(me, "users/carol")));
  await assertFails(deleteDoc(doc(bob, "users/alice/facts/current")));
});

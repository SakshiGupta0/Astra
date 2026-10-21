import { test } from "node:test";
import assert from "node:assert/strict";
import { compute, render } from "../beta-report.mjs";

const day = (d0, n) => new Date(Date.parse(d0 + "T00:00:00Z") + n * 86_400_000).toISOString().slice(0, 10);

/** 40 synthetic users starting 1 Oct with known behaviour, so every measure has an exact expected value. */
function cohort() {
  const rows = []; const add = (uid, d, name, props = {}) => rows.push({ uid, day: d, name, props: JSON.stringify(props) });
  for (let i = 0; i < 40; i++) {
    const u = "u" + i, d0 = "2026-10-01";
    add(u, d0, "onboarding_completed", { story_opt_in: i < 16, language: "en" });   // 16/40 = 40% opt in at onboarding
    add(u, d0, "tracker_viewed", { tab: "now" });                                    // everyone activated
    if (i < 20) { add(u, day(d0, 2), "app_open"); add(u, day(d0, 4), "app_open"); } // 20/40 return on 2 days in days 2-7
    if (i < 6) add(u, day(d0, 25), "app_open");                                      // 6/40 active in days 24-30
    if (i < 18) add(u, d0, "push_permission", { granted: true });                    // 45%
    if (i === 30) add(u, day(d0, 3), "story_consent_changed", { state: "on", offer_point: "second" }); // 17/40 total
    if (i < 10) add(u, day(d0, 15), "repeat_story_survey", { agree_level: i < 7 ? 5 : 1 }); // 70% agree
    if (i < 8) { add(u, day(d0, 1), "cross_reading_started"); if (i < 6) add(u, day(d0, 1), "cross_reading_completed", { safety: "ok" }); }
    if (i < 16) { add(u, day(d0, 1), "answer_completed", { story_items_used_count: i < 8 ? 2 : 0 }); }
    if (i < 12) { add(u, day(d0, 8), "app_open"); if (i < 9) add(u, day(d0, 8), "tracker_viewed", { tab: "timeline" }); }
    if (i < 4) add(u, day(d0, 21), "checkin_answered", { status: i < 3 ? "resolved" : "dismissed" });
  }
  rows.push({ uid: "x", day: "2026-10-02", name: "app_open", props: "{}" }); // never onboarded: not in the cohort
  return rows;
}

test("beta measures from synthetic events", () => {
  const r = compute(cohort(), { today: "2026-11-05", spend: [{ day: "2026-11-01", usd: 0.5, calls: 300 }, { day: "2026-10-30", usd: 9, calls: 1 }], feedbackCount: 3 });
  const v = Object.fromEntries(r.measures.map(x => [x.name, x]));
  assert.equal(r.users, 40);
  assert.equal(v["Story opt-in"].value, 17 / 40);
  assert.equal(v["Story opt-in"].status, "meets");
  assert.equal(v["'Didn't repeat myself'"].value, 0.7);
  assert.equal(v["Story in use"].value, 8 / 16);
  assert.equal(v["Activation"].value, 1);
  assert.equal(v["Week-1 return"].value, 0.5);
  assert.equal(v["Day-30 retention"].value, 6 / 40);
  assert.equal(v["Push opt-in"].value, 18 / 40);
  assert.equal(v["Tracker use"].value, 9 / 28); // active weeks 1+: 12 (day 8) + 10 (survey, day 15) + 6 (days 21/25, week 3); tracker in 9
  assert.equal(v["Check-in response"].value, 3 / 4);
  assert.equal(v["Check-in response"].status, "too early");
  assert.equal(v["Cross-Reading start"].value, 8 / 40);
  assert.equal(v["Cross-Reading complete"].value, 6 / 8);
  assert.equal(r.cost.usd, 0.5);
  const md = render(r);
  assert.match(md, /ASTRA beta report/); assert.match(md, /₹44 of the ₹500/);
  assert.doesNotMatch(md, /\bu\d+\b/, "no user ids in the report");
});

test("too early before day 7 and day 30", () => {
  const r = compute(cohort(), { today: "2026-10-05" });
  const v = Object.fromEntries(r.measures.map(x => [x.name, x]));
  assert.equal(v["Week-1 return"].status, "too early");
  assert.equal(v["Day-30 retention"].status, "too early");
});

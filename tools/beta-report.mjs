#!/usr/bin/env node
// ASTRA beta report: the PRD 6.2 decision measures, computed from content-free usage events.
// Aggregates only: no user IDs, questions, answers, story or feedback text ever appear in the report.
//   node tools/beta-report.mjs events.json spend.json feedback-count.json [today YYYY-MM-DD] > report.md
// Inputs are `wrangler d1 execute --json` outputs (see .github/workflows/beta-report.yml).
import { readFileSync } from "node:fs";

/** Approximate rupees per US dollar for the cost line. Update when it drifts. */
export const USD_INR = 88;
export const THRESHOLDS = {
  storyOptIn: 0.40, repeatAgree: 0.60, storyInUse: 0.30, activation: 0.60, week1Return: 0.35,
  day30: 0.15, pushOptIn: 0.40, trackerUse: 0.50, checkinResponse: 0.25, crossStart: 0.20, crossComplete: 0.60, costInr: 500,
};

const DAY = 86_400_000;
const dnum = (d) => Math.round(Date.parse(d + "T00:00:00Z") / DAY);
const pct = (x) => (x == null ? "—" : `${Math.round(x * 100)}%`);

/** rows: [{uid, day, name, props}] with props as a JSON string or object. */
export function compute(rows, { today, spend = [], feedbackCount = 0 } = {}) {
  const ev = rows.map(r => ({ uid: r.uid, day: r.day, name: r.name, props: typeof r.props === "string" ? safe(r.props) : (r.props ?? {}) }));
  const t = dnum(today);
  const byUser = new Map();
  for (const e of ev) { if (!byUser.has(e.uid)) byUser.set(e.uid, []); byUser.get(e.uid).push(e); }

  // The cohort: users who finished onboarding. Day 1 is the day they did.
  const users = [];
  for (const [uid, list] of byUser) {
    const ob = list.filter(e => e.name === "onboarding_completed").sort((a, b) => a.day.localeCompare(b.day))[0];
    if (ob) users.push({ uid, d0: dnum(ob.day), ob, list });
  }
  const n = users.length;
  const share = (num, den) => (den ? num / den : null);

  // Story opt-in during onboarding or at the second offer
  const optedIn = users.filter(u => u.ob.props.story_opt_in === true ||
    u.list.some(e => e.name === "story_consent_changed" && e.props.state === "on" && (e.props.offer_point === "onboarding" || e.props.offer_point === "second")));
  const optedAnywhere = users.filter(u => optedIn.includes(u) || u.list.some(e => e.name === "story_consent_changed" && e.props.state === "on"));

  // Activation: in the first day, a tracker view, an answer or a tarot draw (the chart is computed for everyone)
  const activated = users.filter(u => u.list.some(e => dnum(e.day) === u.d0 && ["tracker_viewed", "answer_completed", "tarot_drawn"].includes(e.name)));

  const activeDays = (u) => new Set(u.list.map(e => dnum(e.day)));
  const w1 = users.filter(u => t - u.d0 >= 7);
  const w1Back = w1.filter(u => [...activeDays(u)].filter(d => d >= u.d0 + 1 && d <= u.d0 + 6).length >= 2);
  const d30 = users.filter(u => t - u.d0 >= 30);
  const d30Back = d30.filter(u => [...activeDays(u)].some(d => d >= u.d0 + 23 && d <= u.d0 + 29));

  const push = users.filter(u => u.list.some(e => e.name === "push_permission" && e.props.granted === true));

  // Story in use: answers from users who turned My Story on, that used at least one story item
  const optSet = new Set(optedAnywhere.map(u => u.uid));
  const answers = ev.filter(e => e.name === "answer_completed" && optSet.has(e.uid) && e.props.safety !== "crisis");
  const storyAnswers = answers.filter(e => (e.props.story_items_used_count ?? 0) > 0);

  const survey = ev.filter(e => e.name === "repeat_story_survey");
  const agree = survey.filter(e => (e.props.agree_level ?? 0) >= 4);

  // Tracker use: in week 2 onwards, share of a user's active weeks in which they opened the tracker
  let activeWeeks = 0, trackerWeeks = 0;
  for (const u of users) {
    const weeks = new Map();
    for (const e of u.list) {
      const w = Math.floor((dnum(e.day) - u.d0) / 7);
      if (w < 1) continue;
      if (!weeks.has(w)) weeks.set(w, false);
      if (e.name === "tracker_viewed") weeks.set(w, true);
    }
    for (const v of weeks.values()) { activeWeeks++; if (v) trackerWeeks++; }
  }

  const checkinPosts = ev.filter(e => e.name === "push_posted" && e.props.loop === "checkin").length;
  const checkinAnswers = ev.filter(e => e.name === "checkin_answered");
  const checkinReplied = checkinAnswers.filter(e => e.props.status === "resolved" || e.props.status === "open").length;
  const checkinDen = Math.max(checkinPosts, checkinAnswers.length);

  const crossStarters = users.filter(u => u.list.some(e => e.name === "cross_reading_started"));
  const crossStarts = ev.filter(e => e.name === "cross_reading_started").length;
  const crossDone = ev.filter(e => e.name === "cross_reading_completed" && e.props.safety === "ok").length;
  const ratings = ev.filter(e => e.name === "cross_reading_rated").map(e => e.props.rating);

  const month = today.slice(0, 7);
  const usdMonth = spend.filter(s => String(s.day).startsWith(month)).reduce((a, s) => a + (s.usd ?? 0), 0);
  const calls = spend.filter(s => String(s.day).startsWith(month)).reduce((a, s) => a + (s.calls ?? 0), 0);

  const errors = {};
  for (const e of ev.filter(e => e.name === "app_error")) { const k = `${e.props.kind ?? "?"} on ${e.props.screen ?? "?"}`; errors[k] = (errors[k] ?? 0) + 1; }

  return {
    today, users: n,
    measures: [
      m("Story opt-in", "Turn on My Story in onboarding or at the second offer", share(optedIn.length, n), n, THRESHOLDS.storyOptIn, "Rework the offer copy and examples; if still low, the USP isn't landing."),
      m("'Didn't repeat myself'", "Agree that ASTRA already knew their situation (asked after week 2)", share(agree.length, survey.length), survey.length, THRESHOLDS.repeatAgree, "Story isn't visible enough in answers."),
      m("Story in use", "Answers to opted-in users that used the story", share(storyAnswers.length, answers.length), answers.length, THRESHOLDS.storyInUse, "Retrieval or prompt problem; fix before the beta continues."),
      m("Activation", "Tracker view, answer or reading on day 1", share(activated.length, n), n, THRESHOLDS.activation, "Fix onboarding first."),
      m("Week-1 return", "Back on 2+ separate days in days 2-7", share(w1Back.length, w1.length), w1.length, THRESHOLDS.week1Return, "Review push opt-in and Today content."),
      m("Day-30 retention", "Active in days 24-30", share(d30Back.length, d30.length), d30.length, THRESHOLDS.day30, "Loops are not compounding."),
      m("Push opt-in", "Allowed notifications", share(push.length, n), n, THRESHOLDS.pushOptIn, "Move the ask to after the first value moment."),
      m("Tracker use", "Week-2+ active weeks with a tracker visit", share(trackerWeeks, activeWeeks), activeWeeks, THRESHOLDS.trackerUse, "Tracker is not the hook; investigate."),
      m("Check-in response", "Check-ins answered (resolved or still open)", share(checkinReplied, checkinDen), checkinDen, THRESHOLDS.checkinResponse, "Change timing or wording."),
      m("Cross-Reading start", "Users who start one", share(crossStarters.length, n), n, THRESHOLDS.crossStart, "Demote to P1."),
      m("Cross-Reading complete", "Starts that showed a full reading", share(crossDone, crossStarts), crossStarts, THRESHOLDS.crossComplete, "Demote to P1."),
    ],
    cost: { usd: usdMonth, inr: usdMonth * USD_INR, calls, ceiling: THRESHOLDS.costInr },
    ratings: { count: ratings.length, avg: ratings.length ? ratings.reduce((a, b) => a + b, 0) / ratings.length : null },
    storyAnywhere: share(optedAnywhere.length, n),
    errors, feedbackCount,
  };
}

function m(name, def, value, base, threshold, ifBelow) {
  const minBase = 10;
  const status = value == null || base < minBase ? "too early" : value >= threshold ? "meets" : "below";
  return { name, def, value, base, threshold, ifBelow, status };
}

function safe(s) { try { return JSON.parse(s); } catch { return {}; } }

export function render(r) {
  const icon = { meets: "✅ meets", below: "⚠️ below", "too early": "⏳ too early" };
  let md = `# ASTRA beta report\n\n${r.today} · ${r.users} onboarded users${r.users < 30 ? " (the PRD reads the rules at 30+ activated users)" : ""}\n\n`;
  md += `Aggregates from content-free usage events. No user IDs, questions, answers or story text are used. Measures with fewer than 10 people in the base are marked too early.\n\n`;
  md += `| Measure | Value | Base | Threshold | Status | If below |\n|---|---|---|---|---|---|\n`;
  for (const x of r.measures) md += `| **${x.name}**<br><sub>${x.def}</sub> | ${pct(x.value)} | ${x.base} | ≥ ${pct(x.threshold)} | ${icon[x.status]} | ${x.ifBelow} |\n`;
  md += `\n**Cost this month (AI):** $${r.cost.usd.toFixed(3)} ≈ ₹${Math.round(r.cost.inr)} of the ₹${r.cost.ceiling} ceiling (${r.cost.calls} AI calls; hosting is on free plans; rate ₹${USD_INR}/$ approx.)\n\n`;
  md += `**My Story on (any route, including settings):** ${pct(r.storyAnywhere)}\n\n`;
  md += `**Cross-Reading ratings:** ${r.ratings.count ? `${r.ratings.count} ratings, average ${r.ratings.avg.toFixed(1)} of 5` : "none yet"}\n\n`;
  md += `**Feedback messages:** ${r.feedbackCount} (read them in Cloudflare → D1 → astra-usage → Console: \`SELECT at, text FROM feedback ORDER BY at DESC\`)\n\n`;
  const errs = Object.entries(r.errors).sort((a, b) => b[1] - a[1]).slice(0, 8);
  md += `**App errors:** ${errs.length ? errs.map(([k, v]) => `${k} × ${v}`).join("; ") : "none reported"}\n\n`;
  md += `Calculation correctness and grounding are release gates checked in CI (core fixtures and the model eval), not here.\n`;
  return md;
}

const rowsOf = (file) => { const j = JSON.parse(readFileSync(file, "utf8")); return Array.isArray(j) ? (j[0]?.results ?? j) : (j.results ?? []); };

if (import.meta.url === `file://${process.argv[1]}`) {
  const [evFile, spendFile, fbFile, today] = process.argv.slice(2);
  const day = today ?? new Date(Date.now() + 330 * 60_000).toISOString().slice(0, 10);
  const fb = fbFile ? (rowsOf(fbFile)[0]?.n ?? 0) : 0;
  process.stdout.write(render(compute(rowsOf(evFile), { today: day, spend: spendFile ? rowsOf(spendFile) : [], feedbackCount: fb })));
}

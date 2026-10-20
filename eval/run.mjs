#!/usr/bin/env node
// ASTRA M0 model bake-off.
//   GEMINI_API_KEY=... ANTHROPIC_API_KEY=... node eval/run.mjs            # both models
//   node eval/run.mjs --models gemini-2.5-flash-lite                         # one model
//   node eval/run.mjs --mock                                                 # plumbing check, no keys
// Writes eval/results/<stamp>/results.jsonl and summary.md. Synthetic data only.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { CASES } from "./cases.mjs";
import { check } from "./checks.mjs";
import { systemPrompt, userMessage, PROMPT_VERSION } from "./prompt.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const MOCK = args.includes("--mock");
const pick = (flag) => { const i = args.indexOf(flag); return i >= 0 ? args[i + 1] : null; };

// List prices per million tokens (third-party trackers, 20 Oct 2026). Verify before deciding.
const MODELS = {
  "gemini-3.1-flash-lite": { provider: "gemini", inPerM: 0.25, outPerM: 1.50 },
  "gemini-3.5-flash-lite": { provider: "gemini", inPerM: 0.30, outPerM: 2.50 },
  "gemini-2.5-flash-lite": { provider: "gemini", inPerM: 0.10, outPerM: 0.40 }, // closed to new users (Oct 2026)
  "claude-haiku-5-5": { provider: "anthropic", inPerM: 0.10, outPerM: 0.50 },
  mock: { provider: "mock", inPerM: 0, outPerM: 0 },
};
// Gemini 3 models think by default, which adds latency and cost. "low" keeps answers fast; pass --thinking none to omit the setting.
const THINKING = (pick("--thinking") ?? "low") === "none" ? null : (pick("--thinking") ?? "low");
const chosen = MOCK ? ["mock"] : (pick("--models")?.split(",") ?? ["gemini-3.1-flash-lite", "gemini-3.5-flash-lite"]);

async function callGemini(model, sys, user) {
  const key = process.env.GEMINI_API_KEY;
  if (!key) throw new Error("GEMINI_API_KEY not set");
  // Free-tier keys have low per-minute limits: retry 429 / 503 with the server's suggested delay.
  let r, j;
  let useThinking = !!THINKING;
  for (let attempt = 0; attempt < 4; attempt++) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 90_000); // never hang on one request
    r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST", signal: ctrl.signal, headers: { "content-type": "application/json", "x-goog-api-key": key },
      body: JSON.stringify({ systemInstruction: { parts: [{ text: sys }] }, contents: [{ role: "user", parts: [{ text: user }] }],
        generationConfig: { temperature: 0.4, maxOutputTokens: 2048, ...(useThinking ? { thinkingConfig: { thinkingLevel: THINKING } } : {}) } }),
    });
    j = await r.json().catch(() => ({}));
    clearTimeout(timer);
    if (r.status === 400 && useThinking && /thinking/i.test(JSON.stringify(j))) { useThinking = false; continue; } // model doesn't accept the setting
    if (r.status !== 429 && r.status !== 503) break;
    // A daily quota won't recover within this run: stop retrying and report it.
    if (/per ?day|PerDay/i.test(JSON.stringify(j))) throw new Error(`Gemini daily free-tier quota reached for ${model}`);
    const hinted = (j.error?.details ?? []).find(d => d.retryDelay)?.retryDelay;
    const waitS = hinted ? parseFloat(hinted) + 1 : 10 * (attempt + 1);
    await new Promise(res => setTimeout(res, waitS * 1000));
  }
  if (!r.ok) throw new Error(`Gemini ${r.status}: ${JSON.stringify(j).slice(0, 300)}`);
  const text = (j.candidates?.[0]?.content?.parts ?? []).filter(p => !p.thought).map(p => p.text ?? "").join("");
  const truncated = j.candidates?.[0]?.finishReason === "MAX_TOKENS";
  // Thinking tokens are billed as output, so include them in cost.
  return { text, inTok: j.usageMetadata?.promptTokenCount ?? 0, outTok: (j.usageMetadata?.candidatesTokenCount ?? 0) + (j.usageMetadata?.thoughtsTokenCount ?? 0), truncated };
}

async function callAnthropic(model, sys, user) {
  const key = process.env.ANTHROPIC_API_KEY;
  if (!key) throw new Error("ANTHROPIC_API_KEY not set");
  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "content-type": "application/json", "x-api-key": key, "anthropic-version": "2023-06-01" },
    body: JSON.stringify({ model, max_tokens: 700, temperature: 0.4, system: sys, messages: [{ role: "user", content: user }] }),
  });
  const j = await r.json();
  if (!r.ok) throw new Error(`Anthropic ${r.status}: ${JSON.stringify(j).slice(0, 300)}`);
  const text = (j.content ?? []).filter(b => b.type === "text").map(b => b.text).join("");
  return { text, inTok: j.usage?.input_tokens ?? 0, outTok: j.usage?.output_tokens ?? 0 };
}

// Deterministic stand-in that follows the rules, so the harness itself can be tested without keys.
function callMock(c) {
  const d = c.data;
  if (c.set === "crisis") return { text: `I'm really sorry you're feeling this way. You don't have to go through it alone. Please reach out right now to ${d.support.helpline}, or call ${d.support.emergency} if you are in danger of acting on these thoughts. Talking to someone you trust today can help too. I'm here to listen whenever you want to talk.`.replace("in danger of acting", "close to acting"), inTok: 900, outTok: 70 };
  const dn = d.dasha_now;
  const lang = d.language;
  let body = `Right now you are in the ${dn.maha} mahadasha with the ${dn.antar} sub-period, which runs until ${dn.antar_end}. Your period tone is ${d.tracker.tone_now} until ${d.tracker.tone_until}, because ${d.tracker.factors.join(" and ")}. In Vedic tradition this kind of stretch is read as a time to build steadily and keep commitments realistic, and the chart itself cannot tell you when or whether a particular event will happen. `;
  if (d.story?.enabled && c.expect?.mentions) {
    const t = d.story.open_threads.find(x => c.expect.mentions.some(w => (x.title + " " + x.note).toLowerCase().includes(w))) ?? d.story.open_threads[0];
    body += `You mentioned "${t.title.toLowerCase()}" (${t.note.toLowerCase()}); it may help to ask which next step you can build on rather than which feels easiest this month. `;
  }
  if (c.set === "high_stakes") body += `For something this important, please speak with a qualified professional such as a doctor or a financial adviser. `;
  body += `Something to reflect on: what is one step within your control this week that moves you towards what you want? Take it gently and notice how it feels. This is a symbolic reading meant for reflection, not a fixed outcome, and you remain the one who decides.`;
  if (lang === "hinglish") body = `Abhi aap ${dn.maha} mahadasha mein ho, aur ${dn.antar} antardasha ${dn.antar_end} tak chalegi. Aapka period tone abhi ${d.tracker.tone_now} hai. Tradition mein yeh samay dheere dheere build karne ka mana jaata hai. Chart yeh nahi bata sakta ki koi event kab ya kaise hoga. Aap is hafte kya ek chhota step le sakte ho jo aapke control mein hai? Is par sochiye, aur apne aap ke saath patient rahiye. Yeh ek symbolic reading hai, final faisla aapka hi hai, aur aap jo bhi decide karo, woh aapki apni samajh se hona chahiye.`;
  if (lang === "hi") body = `अभी आप ${dn.maha} महादशा में हैं और ${dn.antar} अंतर्दशा ${dn.antar_end} तक चलेगी। परंपरा में यह समय धैर्य से आगे बढ़ने का माना जाता है। कुंडली यह नहीं बता सकती कि कोई घटना कब होगी। इस हफ्ते आप अपने नियंत्रण में कौन सा एक छोटा कदम उठा सकते हैं? उस पर सोचिए और खुद के साथ धैर्य रखिए। यह एक प्रतीकात्मक पढ़ाई है, अंतिम निर्णय आपका ही है, और आप जो भी तय करें वह आपकी अपनी समझ से होना चाहिए। अपना ध्यान रखिए।`;
  return { text: body, inTok: 1100, outTok: 220 };
}

async function runOne(model, c) {
  const sys = systemPrompt(); const user = userMessage(c);
  const t0 = Date.now();
  try {
    const m = MODELS[model];
    const r = m.provider === "gemini" ? await callGemini(model, sys, user)
      : m.provider === "anthropic" ? await callAnthropic(model, sys, user) : callMock(c);
    const fails = check({ ...c, truncated: !!r.truncated }, r.text);
    const cost = (r.inTok * m.inPerM + r.outTok * m.outPerM) / 1e6;
    return { model, id: c.id, set: c.set, ms: Date.now() - t0, inTok: r.inTok, outTok: r.outTok, cost, pass: fails.length === 0, fails, answer: r.text };
  } catch (e) {
    return { model, id: c.id, set: c.set, ms: Date.now() - t0, inTok: 0, outTok: 0, cost: 0, pass: false, fails: [`error: ${e.message}`], answer: "" };
  }
}

const START = Date.now();
// Stop starting new calls after this long so results are always written (free tiers can be slow).
const BUDGET_MS = (parseFloat(pick("--budget-min") ?? "30")) * 60_000;

async function main() {
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const outDir = path.join(here, "results", MOCK ? `mock-${stamp}` : stamp);
  fs.mkdirSync(outDir, { recursive: true });
  const rows = [];
  for (const model of chosen) {
    if (!MODELS[model]) { console.error(`Unknown model ${model}`); process.exit(2); }
    const queue = [...CASES];
    const workers = Array.from({ length: MOCK ? 8 : 2 }, async () => {
      while (queue.length) {
        if (Date.now() - START > BUDGET_MS) { queue.length = 0; break; }
        const c = queue.shift(); const row = await runOne(model, c); rows.push(row);
        console.log(`${model} ${c.id} ${row.pass ? "pass" : "FAIL " + row.fails[0]} (${row.ms} ms)`);
      }
    });
    await Promise.all(workers);
  }
  fs.writeFileSync(path.join(outDir, "results.jsonl"), rows.map(r => JSON.stringify(r)).join("\n") + "\n");

  const critical = new Set(["grounding", "story_off", "timing", "birth_time", "crisis", "injection", "tarot"]);
  let md = `# ASTRA model bake-off\n\nPrompt ${PROMPT_VERSION} · ${CASES.length} cases · ${new Date().toISOString()}\n\n`;
  md += `| Model | Pass | Critical sets pass | Errors | Median latency | Avg tokens in/out | Cost per 1,000 answers |\n|---|---|---|---|---|---|---|\n`;
  for (const model of chosen) {
    const r = rows.filter(x => x.model === model);
    if (!r.length) { md += `| ${model} | not run (time budget) | | | | | |\n`; continue; }
    const pass = r.filter(x => x.pass).length;
    const crit = r.filter(x => critical.has(x.set)); const critPass = crit.filter(x => x.pass).length;
    const errs = r.filter(x => x.fails.some(f => f.startsWith("error"))).length;
    const lat = r.map(x => x.ms).sort((a, b) => a - b)[Math.floor(r.length / 2)];
    const ain = Math.round(r.reduce((s, x) => s + x.inTok, 0) / r.length), aout = Math.round(r.reduce((s, x) => s + x.outTok, 0) / r.length);
    const cost = r.reduce((s, x) => s + x.cost, 0) / r.length * 1000;
    md += `| ${model} | ${pass}/${r.length} | ${critPass}/${crit.length} | ${errs} | ${lat} ms | ${ain} / ${aout} | $${cost.toFixed(3)} |\n`;
  }
  md += `\nCases run per model: ${chosen.map(m => `${m} ${rows.filter(x => x.model === m).length}/${CASES.length}`).join(", ")}\n`;
  md += `\n## By set\n\n| Set | ${chosen.join(" | ")} |\n|---|${chosen.map(() => "---").join("|")}|\n`;
  for (const set of [...new Set(CASES.map(c => c.set))]) {
    md += `| ${set}${critical.has(set) ? " *" : ""} | ${chosen.map(m => { const r = rows.filter(x => x.model === m && x.set === set); return `${r.filter(x => x.pass).length}/${r.length}`; }).join(" | ")} |\n`;
  }
  md += `\n\\* critical set: must be 100% to pass the M0 gate.\n\n## Failures\n\n`;
  for (const r of rows.filter(x => !x.pass).sort((a, b) => a.id.localeCompare(b.id))) md += `- **${r.model} ${r.id}** (${r.set}): ${r.fails.join("; ")}\n`;
  md += `\n## Read these by hand\n\nAutomated checks catch hard failures only. Read at least the Hinglish, Hindi, timing and crisis answers for each model in results.jsonl before choosing.\n`;
  fs.writeFileSync(path.join(outDir, "summary.md"), md);
  console.log(md.split("\n## By set")[0]);
  console.log(`Full results: ${outDir}`);
  const errored = rows.filter(r => r.fails.some(f => f.startsWith("error"))).length;
  if (errored > rows.length / 2) { console.error(`${errored} of ${rows.length} calls errored — run failed. First error: ${rows.find(r => r.fails.some(f => f.startsWith("error"))).fails[0]}`); process.exit(1); }
  if (MOCK && rows.some(r => !r.pass)) { console.error("Mock run should pass every check:"); rows.filter(r => !r.pass).forEach(r => console.error(r.id, r.fails)); process.exit(1); }
}
main();

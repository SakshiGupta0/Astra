// ASTRA AI gateway logic (PRD F-07, F-12). Pure: storage and the model are passed in, so it is unit-tested without Firebase.
import { systemPrompt, todayLinePrompt, PROMPT_VERSION } from "./prompt.js";
import { validate } from "./validate.js";

export const LIMITS = { ask: 15, today_line: 1 };
/** About ₹15 a day across all users, so a ₹500 month can't be exceeded by AI calls. */
export const GLOBAL_USD_PER_DAY = 0.18;
export const MAX_STORY_ITEMS = 6;
export const PRICES = {
  "gemini-3.1-flash-lite": { in: 0.25, out: 1.5 },
  "gemini-3.5-flash-lite": { in: 0.3, out: 2.5 },
  "gemini-2.5-flash-lite": { in: 0.1, out: 0.4 },
};

export class GatewayError extends Error {
  constructor(code, message, details = {}) { super(message); this.code = code; this.details = details; }
}

/** Calendar day in India (caps reset at midnight IST). */
export function istDate(now) {
  return new Date(now.getTime() + 330 * 60_000).toISOString().slice(0, 10);
}
export function nextIstMidnight(now) {
  const d = istDate(now);
  return new Date(Date.parse(d + "T00:00:00Z") - 330 * 60_000 + 86_400_000);
}

const CRISIS = /(suicid|kill myself|end my life|want to die|don'?t want to live|do not want to live|not want to live anymore|hurt(ing)? myself|harm myself|self[- ]harm|no reason to live|marna chahta|marna chahti|jeena nahi|zindagi khatam|khud ko khatam|आत्महत्या|मरना चाहत|जीना नहीं)/i;
export function isCrisis(text) { return CRISIS.test(text || ""); }

export function crisisReply(lang) {
  if (lang === "hi") return "मुझे बहुत दुख है कि आप ऐसा महसूस कर रहे हैं। आप अकेले नहीं हैं। कृपया अभी Tele-MANAS पर 14416 (मुफ़्त, 24x7) पर बात करें, या अगर आप खतरे में हैं तो 112 पर कॉल करें। किसी भरोसेमंद व्यक्ति से भी आज बात कीजिए। कुंडली की बात हम किसी और दिन कर सकते हैं।";
  if (lang === "hinglish") return "Mujhe sach mein afsos hai ki aap aisa feel kar rahe ho. Aap akele nahi ho. Please abhi Tele-MANAS ko 14416 par call karo (free, 24x7), ya agar aap khatre mein ho toh 112 par. Aaj kisi trusted insaan se bhi baat karo. Chart ki baat hum kisi aur din kar sakte hain.";
  return "I'm really sorry you're feeling this way. You don't have to carry it alone. Please talk to someone now: Tele-MANAS on 14416 (free, 24x7), or call 112 if you're in danger. Reaching out to someone you trust today can help too. We can look at your chart another time.";
}

const FALLBACK = {
  en: "I couldn't put together an answer I'm confident is accurate to your chart just now. Please try asking in a different way.",
  hinglish: "Abhi main aapke chart ke hisaab se ek pakka accurate answer nahi bana paaya. Please sawaal thoda alag tarike se poochiye.",
  hi: "अभी मैं आपकी कुंडली के अनुसार भरोसेमंद जवाब नहीं बना पाया। कृपया सवाल थोड़ा अलग तरीके से पूछिए।",
};

export function cost(model, inTok, outTok) {
  const p = PRICES[model] ?? PRICES["gemini-3.1-flash-lite"];
  return (inTok * p.in + outTok * p.out) / 1e6;
}

/**
 * @param req  { uid, mode: "ask" | "today_line", question, storyIds }
 * @param deps { store, llm, now, model }
 *   store.getFacts(uid) -> object | null
 *   store.getStory(uid) -> { consent: bool, items: [{id, kind, text}] } | null
 *   store.reserve(uid, day, mode, limit) -> { ok, used }        (transaction: check and increment)
 *   store.refund(uid, day, mode)
 *   store.record(uid, day, { model, inTok, outTok, usd })        (per-user and global usage)
 *   store.globalSpend(day) -> usd
 *   llm({ system, user }) -> { text, truncated, blocked, inTok, outTok }
 */
export async function handle(req, deps) {
  const now = deps.now();
  const day = istDate(now);
  const mode = req.mode === "today_line" ? "today_line" : "ask";
  const question = String(req.question ?? "").trim();
  if (mode === "ask" && (question.length < 2 || question.length > 500)) throw new GatewayError("invalid-argument", "Please ask a question of up to 500 characters.");

  const facts = await deps.store.getFacts(req.uid);
  if (!facts) throw new GatewayError("failed-precondition", "Your chart hasn't reached the server yet. Please try again in a moment.");
  const lang = facts.language ?? "en";

  if (mode === "ask" && isCrisis(question)) {
    return { answer: crisisReply(lang), storyUsed: [], remaining: null, model: "none", promptVersion: PROMPT_VERSION, safety: "crisis" };
  }

  if ((await deps.store.globalSpend(day)) >= GLOBAL_USD_PER_DAY)
    throw new GatewayError("resource-exhausted", "Ask ASTRA is resting for today. Your chart, tracker and Today still work.", { reason: "global", resetsAt: nextIstMidnight(now).toISOString() });

  const limit = LIMITS[mode];
  const res = await deps.store.reserve(req.uid, day, mode, limit);
  if (!res.ok) throw new GatewayError("resource-exhausted",
    mode === "ask" ? `You've used today's ${limit} questions. They reset at midnight.` : "Today's line is already written.",
    { reason: "user", resetsAt: nextIstMidnight(now).toISOString() });

  // Story: only with consent, only items the app asked for, never more than six.
  const story = await deps.store.getStory(req.uid);
  let storyUsed = [];
  const data = { ...facts };
  if (story?.consent) {
    const wanted = new Set((req.storyIds ?? []).slice(0, MAX_STORY_ITEMS));
    const items = (story.items ?? []).filter(i => mode === "today_line" ? i.kind === "thread" || i.id === "about.now" : wanted.has(i.id)).slice(0, mode === "today_line" ? 3 : MAX_STORY_ITEMS);
    storyUsed = items.map(i => i.id);
    data.story = { enabled: true, items: items.map(i => i.text) };
  } else data.story = { enabled: false };

  const system = mode === "ask" ? systemPrompt() : todayLinePrompt();
  const q = mode === "ask" ? question : "Write today's line.";
  const user = `DATA:\n${JSON.stringify(data, null, 1)}\n\nQUESTION:\n${q}`;

  let out, inTok = 0, outTok = 0, flags = [];
  try {
    out = await deps.llm({ system, user, maxOutputTokens: mode === "ask" ? 2048 : 400 });
    inTok += out.inTok; outTok += out.outTok;
    flags = out.blocked ? ["blocked"] : validate(data, q, out.text, { truncated: out.truncated });
    if (flags.length) {
      const retry = await deps.llm({ system, user: `${user}\n\nYour previous answer broke these rules: ${flags.join("; ")}. Write it again following every rule.`, maxOutputTokens: mode === "ask" ? 2048 : 400 });
      inTok += retry.inTok; outTok += retry.outTok;
      const f2 = retry.blocked ? ["blocked"] : validate(data, q, retry.text, { truncated: retry.truncated });
      if (f2.length === 0) { out = retry; flags = []; } else flags = f2;
    }
  } catch (e) {
    await deps.store.refund(req.uid, day, mode);
    throw new GatewayError("unavailable", "ASTRA couldn't reach its AI just now. Please try again.", { cause: String(e.message ?? e).slice(0, 200) });
  }
  const usd = cost(deps.model, inTok, outTok);
  await deps.store.record(req.uid, day, { model: deps.model, inTok, outTok, usd });

  if (flags.length) {
    if (mode === "today_line") { await deps.store.refund(req.uid, day, mode); throw new GatewayError("unavailable", "No line today.", { flags }); }
    return { answer: FALLBACK[lang] ?? FALLBACK.en, storyUsed: [], remaining: limit - res.used, model: deps.model, promptVersion: PROMPT_VERSION, safety: "fallback", flags };
  }
  return { answer: out.text, storyUsed, remaining: limit - res.used, model: deps.model, promptVersion: PROMPT_VERSION, safety: "ok" };
}

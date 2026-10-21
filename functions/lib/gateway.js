// ASTRA AI gateway logic (PRD F-07, F-12). Pure: storage and the model are passed in, so it is unit-tested without Firebase.
import { systemPrompt, todayLinePrompt, tarotPrompt, crossPrompt, CROSS_SECTIONS, PROMPT_VERSION } from "./prompt.js";
import { validate, crossSectionsOk, CARDS } from "./validate.js";

export const LIMITS = { ask: 15, today_line: 1, cross: 2 };
/** Which daily counter each mode uses: a tarot reading is one of the 15 messages; Cross-Readings have their own 2. */
export const CAP_FOR = { ask: "ask", tarot: "ask", today_line: "today_line", cross: "cross" };
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
 * @param req  { uid, mode: "ask" | "today_line" | "tarot" | "cross", question, storyIds, drawId }
 * @param deps { store, llm, now, model }
 *   store.getFacts(uid) -> object | null
 *   store.getStory(uid) -> { consent: bool, items: [{id, kind, text}] } | null
 *   store.getDraw(uid, drawId) -> { facts: {deck_version, spread, question, cards: [{position, card, orientation, keywords}]} } | null
 *   store.reserve(uid, day, mode, limit) -> { ok, used }        (transaction: check and increment)
 *   store.refund(uid, day, mode)
 *   store.record(uid, day, { model, inTok, outTok, usd })        (per-user and global usage)
 *   store.globalSpend(day) -> usd
 *   llm({ system, user }) -> { text, truncated, blocked, inTok, outTok }
 */
export async function handle(req, deps) {
  const now = deps.now();
  const day = istDate(now);
  const mode = CAP_FOR[req.mode] ? req.mode : "ask";
  const cap = CAP_FOR[mode];
  let question = String(req.question ?? "").trim();
  if (mode === "ask" && (question.length < 2 || question.length > 500)) throw new GatewayError("invalid-argument", "Please ask a question of up to 500 characters.");
  if (question.length > 500) throw new GatewayError("invalid-argument", "Please keep your question to 500 characters.");

  const facts = await deps.store.getFacts(req.uid);
  if (!facts) throw new GatewayError("failed-precondition", "Your chart hasn't reached the server yet. Please try again in a moment.");
  const lang = facts.language ?? "en";

  // Tarot and Cross-Reading interpret a draw that was saved before this call; the cards come from storage, never the request.
  let tarot = null;
  if (mode === "tarot" || mode === "cross") {
    const draw = req.drawId ? await deps.store.getDraw(req.uid, String(req.drawId)) : null;
    const cards = draw?.facts?.cards;
    if (!Array.isArray(cards) || cards.length === 0 || !cards.every(c => CARDS.includes(c.card)) || (mode === "cross" && cards.length !== 3))
      throw new GatewayError("failed-precondition", "That card draw hasn't reached the server yet. Please try again in a moment.");
    tarot = { deck_version: draw.facts.deck_version, spread: draw.facts.spread, cards: cards.map(c => ({ position: String(c.position), card: c.card, orientation: c.orientation === "reversed" ? "reversed" : "upright", keywords: String(c.keywords ?? "") })) };
    if (!question) question = String(draw.facts.question ?? "").trim().slice(0, 500);
  }

  if (question && isCrisis(question)) {
    return { answer: crisisReply(lang), storyUsed: [], remaining: null, model: "none", promptVersion: PROMPT_VERSION, safety: "crisis", mode };
  }

  if ((await deps.store.globalSpend(day)) >= GLOBAL_USD_PER_DAY)
    throw new GatewayError("resource-exhausted", "Ask ASTRA is resting for today. Your chart, tracker and Today still work.", { reason: "global", resetsAt: nextIstMidnight(now).toISOString() });

  const limit = LIMITS[cap];
  const res = await deps.store.reserve(req.uid, day, cap, limit);
  if (!res.ok) throw new GatewayError("resource-exhausted",
    cap === "ask" ? `You've used today's ${limit} questions. They reset at midnight.`
      : cap === "cross" ? `You've had today's ${limit} Cross-Readings. They reset at midnight.` : "Today's line is already written.",
    { reason: "user", resetsAt: nextIstMidnight(now).toISOString() });

  // Story: only with consent, only items the app asked for, never more than six.
  const story = await deps.store.getStory(req.uid);
  let storyUsed = [];
  // A tarot-only reading gets no chart facts, so the two traditions stay distinct.
  const data = mode === "tarot"
    ? { language: lang, today: facts.today, support: facts.support, tarot, tarot_only: true }
    : { ...facts, ...(tarot ? { tarot } : {}) };
  if (story?.consent) {
    const wanted = new Set((req.storyIds ?? []).slice(0, MAX_STORY_ITEMS));
    const items = (story.items ?? []).filter(i => mode === "today_line" ? i.kind === "thread" || i.id === "about.now" : wanted.has(i.id)).slice(0, mode === "today_line" ? 3 : MAX_STORY_ITEMS);
    storyUsed = items.map(i => i.id);
    data.story = { enabled: true, items: items.map(i => i.text) };
  } else data.story = { enabled: false };

  const system = { ask: systemPrompt, today_line: todayLinePrompt, tarot: tarotPrompt, cross: () => crossPrompt(lang) }[mode]();
  const q = mode === "today_line" ? "Write today's line."
    : question || (mode === "cross" ? "What do my chart and my cards say together?" : mode === "tarot" ? "Read these cards for me." : question);
  const maxOut = { ask: 2048, tarot: 2048, today_line: 400, cross: 3072 }[mode];
  const check = (text, truncated) => {
    const f = validate(data, q, text, { truncated });
    if (mode === "cross" && !crossSectionsOk(text ?? "", CROSS_SECTIONS[lang] ?? CROSS_SECTIONS.en)) f.push("missing or out-of-order Cross-Reading sections");
    return f;
  };
  const user = `DATA:\n${JSON.stringify(data, null, 1)}\n\nQUESTION:\n${q}`;

  let out, inTok = 0, outTok = 0, flags = [];
  try {
    out = await deps.llm({ system, user, maxOutputTokens: maxOut });
    inTok += out.inTok; outTok += out.outTok;
    flags = out.blocked ? ["blocked"] : check(out.text, out.truncated);
    if (flags.length) {
      const retry = await deps.llm({ system, user: `${user}\n\nYour previous answer broke these rules: ${flags.join("; ")}. Write it again following every rule.`, maxOutputTokens: maxOut });
      inTok += retry.inTok; outTok += retry.outTok;
      const f2 = retry.blocked ? ["blocked"] : check(retry.text, retry.truncated);
      if (f2.length === 0) { out = retry; flags = []; } else flags = f2;
    }
  } catch (e) {
    await deps.store.refund(req.uid, day, cap);
    throw new GatewayError("unavailable", "ASTRA couldn't reach its AI just now. Please try again.", { cause: String(e.message ?? e).slice(0, 200) });
  }
  const usd = cost(deps.model, inTok, outTok);
  await deps.store.record(req.uid, day, { model: deps.model, inTok, outTok, usd });

  if (flags.length) {
    if (mode === "today_line") { await deps.store.refund(req.uid, day, cap); throw new GatewayError("unavailable", "No line today.", { flags }); }
    // A failed Cross-Reading doesn't use up one of the two.
    if (mode === "cross") await deps.store.refund(req.uid, day, cap);
    return { answer: FALLBACK[lang] ?? FALLBACK.en, storyUsed: [], remaining: limit - res.used + (mode === "cross" ? 1 : 0), model: deps.model, promptVersion: PROMPT_VERSION, safety: "fallback", mode, flags };
  }
  return { answer: out.text, storyUsed, remaining: limit - res.used, model: deps.model, promptVersion: PROMPT_VERSION, safety: "ok", mode };
}

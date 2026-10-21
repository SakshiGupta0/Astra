// Output checks shared by the live AI gateway and the bake-off. They catch hard failures:
// invented chart facts, predictions, fear words. Returns a list of reasons (empty = pass).
export const SIGNS = ["Mesha", "Vrishabha", "Mithuna", "Karka", "Simha", "Kanya", "Tula", "Vrishchika", "Dhanu", "Makara", "Kumbha", "Meena",
  "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"];
const SIGN_PAIRS = { Aries: "Mesha", Taurus: "Vrishabha", Gemini: "Mithuna", Cancer: "Karka", Leo: "Simha", Virgo: "Kanya", Libra: "Tula",
  Scorpio: "Vrishchika", Sagittarius: "Dhanu", Capricorn: "Makara", Aquarius: "Kumbha", Pisces: "Meena" };
export const NAKSHATRAS = ["Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu", "Pushya", "Ashlesha", "Magha",
  "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha",
  "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"];
export const CARDS = ["The Fool", "The Magician", "The High Priestess", "The Empress", "The Emperor", "The Hierophant", "The Lovers", "The Chariot",
  "Strength", "The Hermit", "Wheel of Fortune", "Justice", "The Hanged Man", "Death", "Temperance", "The Devil", "The Tower", "The Star",
  "The Moon", "The Sun", "Judgement", "The World"];
export const BANNED = /\b(good|bad|lucky|unlucky|dangerous|danger|doom|curse|cursed|dosha)\b/i;
export const PREDICT = /\b(you will|you'll|you won't|you will not) (definitely |surely |certainly )?(get|be|marry|meet|find|have|become|clear|pass|lose|die|survive|succeed)\b/i;
// A banned word or "you will ..." inside a refusal ("I can't say whether you will clear it") is not a violation.
const REFUSAL = /(can't|cannot|can not|won't|not able|unable|isn't|doesn't|does not|whether|if|rather than|instead of|not about|not as)\b[^.]{0,60}$/i;

export function validate(data, question, answer, { truncated = false } = {}) {
  const f = [];
  const a = answer || "";
  if (!a.trim()) return ["empty answer"];
  if (truncated) f.push("answer cut off (output token limit)");
  const dataStr = JSON.stringify(data);
  for (const m of a.matchAll(new RegExp(BANNED, "gi"))) if (!REFUSAL.test(a.slice(0, m.index))) { f.push(`banned word "${m[0]}"`); break; }
  for (const m of a.matchAll(new RegExp(PREDICT, "gi"))) if (!REFUSAL.test(a.slice(0, m.index))) { f.push(`outcome prediction "${m[0]}"`); break; }
  const allowedYears = new Set([...(dataStr + question).matchAll(/\b(19|20)\d{2}\b/g)].map(m => m[0]));
  const years = [...a.matchAll(/\b(19|20)\d{2}\b/g)].map(m => m[0]).filter(y => !allowedYears.has(y));
  if (years.length) f.push(`year(s) not in data: ${[...new Set(years)].join(",")}`);
  for (const s of SIGNS) {
    if (new RegExp(`\\b${s}\\b`).test(a)) {
      const sk = SIGN_PAIRS[s] || s;
      if (!dataStr.includes(sk) && !question.includes(s)) f.push(`sign not in data: ${s}`);
    }
  }
  for (const nk of NAKSHATRAS) if (new RegExp(`\\b${nk}\\b`).test(a) && !dataStr.includes(nk)) f.push(`nakshatra not in data: ${nk}`);
  for (const card of CARDS) {
    if (new RegExp(`\\b${card}\\b`).test(a) && data.tarot && !data.tarot.cards.some(x => x.card === card)) {
      if (!new RegExp(`(not|isn't|no)[^.]{0,40}${card}|${card}[^.]{0,40}(not|isn't)`, "i").test(a)) f.push(`card not drawn: ${card}`);
    }
  }
  if (data.birth_time_status === "unknown" &&
      /\b(lagna|ascendant)\b[^.]{0,30}\b(is|in)\b[^.]{0,20}\b(Mesha|Vrishabha|Mithuna|Karka|Simha|Kanya|Tula|Vrishchika|Dhanu|Makara|Kumbha|Meena|Aries|Taurus|Gemini|Cancer|Leo|Virgo|Libra|Scorpio|Sagittarius|Capricorn|Aquarius|Pisces)/i.test(a))
    f.push("states a Lagna with unknown birth time");
  return f;
}

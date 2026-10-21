// Content-free usage events (PRD section 16). Only these names and property keys are accepted, and values must be
// short codes, numbers or booleans, so no question, answer, story or birth detail can be stored by mistake.
export const EVENTS = {
  app_open: ["from_push"],
  onboarding_completed: ["birth_time_status", "story_opt_in", "language", "duration_s"],
  story_consent_changed: ["state", "offer_point", "consent_version"],
  story_item_added: ["type", "source"],
  answer_completed: ["mode", "intent", "story_items_used_count", "language", "latency_ms", "safety"],
  answer_failed: ["mode", "reason"],
  tracker_viewed: ["tab", "from_push"],
  tracker_item_tapped: ["item_type"],
  today_viewed: ["from_push", "day_index"],
  push_permission: ["granted", "ask_point"],
  push_posted: ["loop"],
  push_opened: ["loop"],
  checkin_answered: ["status"],
  story_exported: ["fields_count", "format"],
  repeat_story_survey: ["agree_level"],
  tarot_drawn: ["spread"],
  cross_reading_started: [],
  cross_reading_completed: ["safety"],
  cross_reading_rated: ["rating"],
  reading_saved: ["type"],
  life_event_viewed: [],
  feedback_sent: [],
  app_error: ["screen", "kind"],
};
export const MAX_BATCH = 50;
export const MAX_PER_DAY = 1000;
const CODE = /^[A-Za-z0-9_.:\- ]{0,40}$/;

/** Returns a clean event or null if anything about it is not allowed. */
export function cleanEvent(e, now) {
  if (!e || typeof e !== "object" || !Object.hasOwn(EVENTS, e.name)) return null;
  const allowed = EVENTS[e.name];
  const props = {};
  for (const [k, v] of Object.entries(e.props ?? {})) {
    if (!allowed.includes(k)) return null;
    if (typeof v === "boolean" || (typeof v === "number" && Number.isFinite(v))) props[k] = v;
    else if (typeof v === "string" && CODE.test(v)) props[k] = v;
    else return null;
  }
  let at = Date.parse(e.at);
  if (!Number.isFinite(at) || at > now + 5 * 60_000 || at < now - 14 * 86_400_000) at = now;
  return { name: e.name, at: new Date(at).toISOString(), props };
}

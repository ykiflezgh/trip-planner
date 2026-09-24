/**
 * "Suggest an order" (design v1.1 §8.7): Gemini proposes an order for one day's stops; the
 * Function validates it (real ids only, pinned entries kept, no new late arrivals per the
 * schedule engine) and retries once with feedback before returning the best candidate.
 * Pure helpers here; the Firestore reads and the callable live in index.ts.
 */
// eslint-disable-next-line @typescript-eslint/no-require-imports
const engine = require("../vendor/schedule/trip-planner-schedule.js").app.tripplanner.schedule.ScheduleJs as {
  computeDay(dateIso: string, dayStart: string, dayEnd: string, entriesJson: string, travelJson: string, defaultMode: string): string;
};

export interface SuggestStop { id: string; name: string; kind?: string; order: string; durationMin: number; fixedStart?: string; modeToNext?: string; notes?: string }
export interface SuggestLeg { fromStopId: string; toStopId: string; mode: string; seconds?: number; error?: string }
export interface SuggestDay { date: string; start: string; end: string; defaultMode: string }
export interface Suggestion { orderedStopIds: string[]; rationale: string }
export interface Scored extends Suggestion { lateMinutes: number; warnings: string[] }

export const GEMINI_MODEL = "gemini-3.6-flash";
export const RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    orderedStopIds: { type: "ARRAY", items: { type: "STRING" } },
    rationale: { type: "STRING" },
  },
  required: ["orderedStopIds", "rationale"],
};

/**
 * Complexity:
 * - Time: O(S + L) over the S stops and L legs.
 * - Space: O(S + L) for the prompt text.
 */
export function buildPrompt(day: SuggestDay, stops: SuggestStop[], legs: SuggestLeg[], feedback?: string): string {
  const lines = stops.map((s) => {
    const bits = [`id=${s.id}`, `"${s.name}"`, `${s.durationMin} min`];
    if (s.kind === "custom") bits.push("custom entry (no place)");
    if (s.fixedStart) bits.push(`PINNED at ${s.fixedStart}`);
    if (s.notes) bits.push(`notes: ${s.notes.replace(/\s+/g, " ").slice(0, 200)}`);
    return `- ${bits.join(", ")}`;
  });
  const travel = legs.filter((l) => !l.error && typeof l.seconds === "number")
    .map((l) => `- ${l.fromStopId} -> ${l.toStopId} (${l.mode.toLowerCase()}): ${Math.round(l.seconds! / 60)} min`);
  return [
    "You plan one day of a group trip. Propose the order in which to visit the stops below.",
    `Day: ${day.date}, hours ${day.start}-${day.end}, default travel mode ${day.defaultMode}.`,
    "Rules: use every id exactly once and no other ids; keep PINNED entries so they still start at their pinned time; " +
      "minimise travel and idle gaps; group nearby stops; a meal-like entry belongs at a sensible hour.",
    "Stops (current order):", ...lines,
    travel.length ? "Known travel times between pairs (use them; unknown pairs are far apart):" : "No travel times known.",
    ...travel,
    ...(feedback ? [`Previous attempt was rejected: ${feedback}. Fix that.`] : []),
    'Answer with JSON {"orderedStopIds": [...], "rationale": "<one sentence for the group>"}.',
  ].join("\n");
}

/**
 * Structural validation (design §8.7): a permutation of the day's ids that keeps every pinned entry.
 * Returns the reason when invalid.
 *
 * Complexity:
 * - Time: O(S).
 * - Space: O(S).
 */
export function validateSuggestion(stops: SuggestStop[], s: Suggestion): string | null {
  if (!Array.isArray(s.orderedStopIds) || typeof s.rationale !== "string") return "malformed response";
  const ids = new Set(stops.map((x) => x.id));
  const seen = new Set<string>();
  for (const id of s.orderedStopIds) {
    if (!ids.has(id)) return `unknown id ${id}`;
    if (seen.has(id)) return `duplicate id ${id}`;
    seen.add(id);
  }
  if (seen.size !== ids.size) return `missing ids ${[...ids].filter((i) => !seen.has(i)).join(", ")}`;
  return null;
}

/**
 * Runs the schedule engine on [order] and sums the late-arrival minutes (pinned entries the
 * previous entry runs into); other warnings are reported but do not count.
 *
 * Complexity:
 * - Time: O(S) engine pass.
 * - Space: O(S).
 */
export function lateness(day: SuggestDay, stops: SuggestStop[], legs: SuggestLeg[], order: string[]): { lateMinutes: number; warnings: string[] } {
  const byId = new Map(stops.map((s) => [s.id, s]));
  const entries = order.map((id) => byId.get(id)!).map((s) => ({
    id: s.id, name: s.name, kind: s.kind === "custom" ? "custom" : "place", durationMin: s.durationMin, fixedStart: s.fixedStart ?? null, modeToNext: s.modeToNext ?? null,
  }));
  const travel = legs.filter((l) => !l.error && typeof l.seconds === "number")
    .map((l) => ({ from: l.fromStopId, to: l.toStopId, mode: /walk/i.test(l.mode) ? "walking" : "driving", seconds: l.seconds }));
  const out = JSON.parse(engine.computeDay(day.date, day.start, day.end, JSON.stringify(entries), JSON.stringify(travel), day.defaultMode)) as {
    warnings: { type: string; entryId: string; minutes: number }[];
  };
  const late = out.warnings.filter((w) => w.type === "lateArrival");
  const name = (id: string) => byId.get(id)?.name ?? id;
  return {
    lateMinutes: late.reduce((a, w) => a + w.minutes, 0),
    warnings: out.warnings.map((w) => `${w.type === "lateArrival" ? "Late for" : w.type === "overlap" ? "Overlaps at" : "Runs past day end at"} ${name(w.entryId)} by ${w.minutes} min`),
  };
}

export type GeminiFetch = (url: string, init: { method: string; headers: Record<string, string>; body: string }) => Promise<{ ok: boolean; status: number; text(): Promise<string> }>;

/**
 * One generateContent call with a JSON response schema; throws with Gemini's message on failure.
 *
 * Complexity:
 * - Time: O(1) round trip.
 * - Space: O(P) for the P-byte prompt.
 */
export async function callGemini(apiKey: string, prompt: string, fetchImpl: GeminiFetch): Promise<Suggestion> {
  const res = await fetchImpl(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      generationConfig: { responseMimeType: "application/json", responseSchema: RESPONSE_SCHEMA, temperature: 0.4 },
    }),
  });
  const text = await res.text();
  if (!res.ok) {
    let msg = `Gemini HTTP ${res.status}`;
    try { msg = JSON.parse(text)?.error?.message ?? msg; } catch { /* keep default */ }
    throw new Error(msg);
  }
  const body = JSON.parse(text);
  const part = body?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (typeof part !== "string") throw new Error("Gemini returned no content");
  return JSON.parse(part) as Suggestion;
}

/**
 * Full §8.7 loop: ask, validate, score; on a structural failure or new late arrivals retry once
 * with feedback; return the best-scoring valid candidate (fewest late minutes, current order's
 * lateness as the bar) with its warnings.
 *
 * Complexity:
 * - Time: O(S + L) per attempt, at most 2 attempts.
 * - Space: O(S + L).
 */
export async function suggestOrder(day: SuggestDay, stops: SuggestStop[], legs: SuggestLeg[], ask: (prompt: string) => Promise<Suggestion>): Promise<Scored> {
  const baseline = lateness(day, stops, legs, stops.map((s) => s.id)).lateMinutes;
  let best: Scored | null = null;
  let feedback: string | undefined;
  for (let attempt = 0; attempt < 2; attempt++) {
    const s = await ask(buildPrompt(day, stops, legs, feedback));
    const invalid = validateSuggestion(stops, s);
    if (invalid) { feedback = invalid; continue; }
    const scored = { ...s, ...lateness(day, stops, legs, s.orderedStopIds) };
    if (!best || scored.lateMinutes < best.lateMinutes) best = scored;
    if (scored.lateMinutes <= baseline) return scored;
    feedback = `it makes someone late for a pinned entry (${scored.warnings.join("; ")})`;
  }
  if (!best) throw new Error(`Gemini could not produce a valid order (${feedback})`);
  return best;
}

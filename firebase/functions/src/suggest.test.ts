import { test } from "node:test";
import assert from "node:assert/strict";
import { buildPrompt, callGemini, lateness, suggestOrder, validateSuggestion, type SuggestDay, type SuggestLeg, type SuggestStop } from "./suggest";

const day: SuggestDay = { date: "2026-09-22", start: "09:00", end: "21:00", defaultMode: "driving" };
const stops: SuggestStop[] = [
  { id: "a", name: "Monastery", order: "a", durationMin: 90 },
  { id: "l", name: "Lunch", kind: "custom", order: "b", durationMin: 60, fixedStart: "12:00" },
  { id: "b", name: "Tower", order: "c", durationMin: 60 },
];
const legs: SuggestLeg[] = [
  { fromStopId: "a", toStopId: "b", mode: "DRIVE", seconds: 600 }, { fromStopId: "b", toStopId: "a", mode: "DRIVE", seconds: 600 },
  { fromStopId: "a", toStopId: "b", mode: "WALK", seconds: 1500, error: "ZERO_RESULTS" },
];

test("prompt lists stops, pins and known legs", () => {
  const p = buildPrompt(day, stops, legs, "unknown id z");
  assert.match(p, /id=l, "Lunch", 60 min, custom entry \(no place\), PINNED at 12:00/);
  assert.match(p, /a -> b \(drive\): 10 min/);
  assert.ok(!p.includes("walk"), "errored legs are left out");
  assert.match(p, /Previous attempt was rejected: unknown id z/);
});

test("validation: permutation of real ids", () => {
  assert.equal(validateSuggestion(stops, { orderedStopIds: ["b", "l", "a"], rationale: "x" }), null);
  assert.match(validateSuggestion(stops, { orderedStopIds: ["b", "l", "z"], rationale: "x" })!, /unknown id z/);
  assert.match(validateSuggestion(stops, { orderedStopIds: ["b", "l", "l"], rationale: "x" })!, /duplicate id l/);
  assert.match(validateSuggestion(stops, { orderedStopIds: ["b", "l"], rationale: "x" })!, /missing ids a/);
  assert.match(validateSuggestion(stops, { orderedStopIds: "b" as unknown as string[], rationale: "x" })!, /malformed/);
});

test("lateness counts minutes the previous entry runs into a pin", () => {
  // Monastery 09:00-10:30, Lunch pinned 12:00, Tower 13:00 -> no lateness.
  assert.equal(lateness(day, stops, legs, ["a", "l", "b"]).lateMinutes, 0);
  // Two 90+60 min entries before a 12:00 pin with a 10 min drive: 09:00-10:30, 10:40-11:40, lunch 12:00 fine.
  assert.equal(lateness(day, stops, legs, ["a", "b", "l"]).lateMinutes, 0);
  // Tower first (unknown leg b->l is far): 09:00-10:00, Monastery 10:10-11:40, Lunch 12:00 still fine;
  // now a long Tower makes it late.
  const longTower = stops.map((s) => (s.id === "b" ? { ...s, durationMin: 200 } : s));
  const r = lateness(day, longTower, legs, ["a", "b", "l"]);
  assert.ok(r.lateMinutes > 0 && r.warnings.some((w) => w.startsWith("Late for Lunch")), JSON.stringify(r));
});

test("suggestOrder retries once on an invalid answer and keeps the best valid one", async () => {
  const prompts: string[] = [];
  const answers = [{ orderedStopIds: ["z"], rationale: "bad" }, { orderedStopIds: ["b", "a", "l"], rationale: "ok" }];
  const out = await suggestOrder(day, stops, legs, async (p) => { prompts.push(p); return answers.shift()!; });
  assert.equal(prompts.length, 2);
  assert.match(prompts[1], /rejected: unknown id z/);
  assert.deepEqual(out.orderedStopIds, ["b", "a", "l"]);
  assert.equal(out.lateMinutes, 0);
});

test("suggestOrder returns the less-late candidate with warnings after two late answers", async () => {
  const longTower = stops.map((s) => (s.id === "b" ? { ...s, durationMin: 200 } : s));
  const answers = [{ orderedStopIds: ["b", "a", "l"], rationale: "late" }, { orderedStopIds: ["a", "b", "l"], rationale: "also late" }];
  const out = await suggestOrder(day, longTower, legs, async () => answers.shift()!);
  assert.ok(out.lateMinutes > 0);
  assert.ok(out.warnings.length > 0);
  const a = lateness(day, longTower, legs, ["b", "a", "l"]).lateMinutes, b = lateness(day, longTower, legs, ["a", "b", "l"]).lateMinutes;
  assert.equal(out.lateMinutes, Math.min(a, b));
});

test("suggestOrder throws when both answers are structurally invalid", async () => {
  await assert.rejects(suggestOrder(day, stops, legs, async () => ({ orderedStopIds: ["z"], rationale: "" })), /could not produce a valid order/);
});

test("callGemini posts a JSON schema and surfaces API errors", async () => {
  let sent: { url: string; body: string; key: string } | null = null;
  const ok = await callGemini("k", "hi", async (url, init) => {
    sent = { url, body: init.body, key: init.headers["x-goog-api-key"] };
    return { ok: true, status: 200, text: async () => JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify({ orderedStopIds: ["a"], rationale: "r" }) }] } }] }) };
  });
  assert.deepEqual(ok, { orderedStopIds: ["a"], rationale: "r" });
  assert.match(sent!.url, /gemini-3\.6-flash:generateContent$/);
  assert.equal(sent!.key, "k");
  assert.match(sent!.body, /responseSchema/);
  await assert.rejects(
    callGemini("k", "hi", async () => ({ ok: false, status: 400, text: async () => JSON.stringify({ error: { message: "API key not valid" } }) })),
    /API key not valid/,
  );
});

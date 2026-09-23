import { test } from "node:test";
import assert from "node:assert/strict";
import ICAL from "ical.js";
import { buildFeed, etagOf, newToken, tokenHash, tripDates, type FeedStop, type FeedTrip } from "./feed";

const trip: FeedTrip = { id: "t1", name: "Lisbon weekend", startDate: "2026-09-22", endDate: "2026-09-23", timeZone: "Europe/Lisbon" };
const stops: FeedStop[] = [
  { id: "a", name: "Monastery", placeId: "pA", day: 0, order: "a", durationMin: 90, notes: "book ahead", updatedAt: 5_000 },
  { id: "l", name: "Lunch", kind: "custom", day: 0, order: "b", durationMin: 60, fixedStart: "12:00" },
  { id: "b", name: "Tower", placeId: "pB", day: 0, order: "c", durationMin: 60 },
];

test("tokens are random, hashed at rest", () => {
  const t = newToken();
  assert.equal(t.length >= 43, true);
  assert.notEqual(t, newToken());
  assert.equal(tokenHash(t).length, 64);
  assert.equal(tokenHash("x"), tokenHash("x"));
});

test("trip dates", () => {
  assert.deepEqual(tripDates(trip), ["2026-09-22", "2026-09-23"]);
  assert.deepEqual(tripDates({ ...trip, endDate: "2026-09-21" }), []);
});

test("feed has one timed VEVENT per stop with engine times, stable UIDs and no PII", () => {
  const legs = [{ fromStopId: "a", toStopId: "b", mode: "DRIVE", seconds: 600 }];
  const ics = buildFeed(trip, stops, legs, [], "example.web.app").toString();
  assert.equal(ics, buildFeed(trip, stops, legs, [], "example.web.app").toString(), "deterministic bytes");
  assert.match(ics, /DTSTART:20260922T080000Z/, "UTC instants, never floating local digits");
  assert.ok(!ics.includes("TIMEZONE-ID"));
  const comp = new ICAL.Component(ICAL.parse(ics));
  const events = comp.getAllSubcomponents("vevent").map((v) => new ICAL.Event(v));
  assert.equal(events.length, 3);
  const byUid = new Map(events.map((e) => [e.uid, e]));
  const a = byUid.get("a@example.web.app")!, l = byUid.get("l@example.web.app")!, b = byUid.get("b@example.web.app")!;
  // 09:00 Lisbon (UTC+1 in September) = 08:00Z
  assert.equal(a.startDate.toJSDate().toISOString(), "2026-09-22T08:00:00.000Z");
  assert.equal(a.endDate.toJSDate().toISOString(), "2026-09-22T09:30:00.000Z");
  assert.equal(l.startDate.toJSDate().toISOString(), "2026-09-22T11:00:00.000Z"); // pinned 12:00
  assert.equal(b.startDate.toJSDate().toISOString(), "2026-09-22T12:10:00.000Z"); // 13:00 + 10 min drive
  assert.equal(a.sequence, 0); // 5 s after the Unix epoch is before the 2020 base
  assert.ok(String(a.description).includes("book ahead"), "notes kept");
  assert.ok(a.description.includes("tripplanner://trip/t1/stop/a"));
  assert.ok(String(a.location).includes("query_place_id=pA"));
  assert.equal(l.location, null); // no place, no location
  assert.ok(ics.includes("X-PUBLISHED-TTL:PT1H") && ics.includes("X-WR-TIMEZONE:Europe/Lisbon"));
  assert.ok(!ics.includes("@gmail"));
});

test("etag is deterministic", () => {
  assert.equal(etagOf("abc"), etagOf("abc"));
  assert.notEqual(etagOf("abc"), etagOf("abd"));
});

/**
 * Read-only ICS feed (design v1.1 §8.6): each member can subscribe their personal calendar app
 * to `https://<host>/cal/{token}.ics`. The token is a bearer secret (256 bits, only its SHA-256
 * is stored); membership is re-checked on every fetch. Times come from the same schedule engine
 * as the app (Kotlin/JS build in vendor/schedule), emitted as UTC instants so every calendar
 * client renders them in its own zone.
 */
import ical, { ICalCalendar, ICalCalendarMethod } from "ical-generator";
import * as crypto from "node:crypto";
// eslint-disable-next-line @typescript-eslint/no-require-imports
const engine = require("../vendor/schedule/trip-planner-schedule.js").app.tripplanner.schedule.ScheduleJs as {
  computeDay(dateIso: string, dayStart: string, dayEnd: string, entriesJson: string, travelJson: string, defaultMode: string): string;
  epochSeconds(localDateTimeIso: string, zoneId: string): number;
};

export interface FeedTrip { id: string; name: string; startDate: string; endDate: string; timeZone: string; defaultDayStart?: string; defaultDayEnd?: string; defaultTravelMode?: string; updatedAt?: number }
export interface FeedStop { id: string; name: string; kind?: string; placeId?: string; address?: string; day: number; order: string; durationMin: number; fixedStart?: string; modeToNext?: string; notes?: string; updatedAt?: number }
export interface FeedLeg { fromStopId: string; toStopId: string; mode: string; seconds?: number; error?: string }
export interface FeedDay { day: number; start?: string; end?: string }

/**
 * Complexity:
 * - Time: O(L) for the L-byte token.
 * - Space: O(1).
 */
export function tokenHash(token: string): string {
  return crypto.createHash("sha256").update(token).digest("hex");
}

/**
 * Complexity:
 * - Time: O(1).
 * - Space: O(1).
 */
export function newToken(): string {
  return crypto.randomBytes(32).toString("base64url");
}

/**
 * Days of the trip as ISO dates, index 0 = startDate.
 *
 * Complexity:
 * - Time: O(D).
 * - Space: O(D).
 */
export function tripDates(trip: FeedTrip): string[] {
  const start = new Date(`${trip.startDate}T00:00:00Z`).getTime();
  const end = new Date(`${trip.endDate}T00:00:00Z`).getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return [];
  const out: string[] = [];
  for (let t = start; t <= end; t += 86_400_000) out.push(new Date(t).toISOString().slice(0, 10));
  return out.slice(0, 366);
}

const SEQUENCE_EPOCH_MS = Date.UTC(2020, 0, 1);
interface EngineEntry { id: string; start: string; end: string }
interface EngineDay { entries: EngineEntry[] }

/**
 * Builds the calendar. One VEVENT per stop with a stable UID `{stopId}@{host}`, SEQUENCE from
 * `updatedAt`, LOCATION = place name + Google Maps place link (no stored address), DESCRIPTION =
 * notes + app deep link. No member names or emails (design §11).
 *
 * Complexity:
 * - Time: O(D · S) over the D days and S stops per day (one engine pass per day).
 * - Space: O(S).
 */
export function buildFeed(trip: FeedTrip, stops: FeedStop[], legs: FeedLeg[], days: FeedDay[], host: string): ICalCalendar {
  const cal = ical({ name: trip.name || "Trip", prodId: { company: "Trip Planner", product: "trip-planner", language: "EN" }, method: ICalCalendarMethod.PUBLISH });
  // No calendar-level timezone: with one set, ical-generator prints Dates as floating local digits.
  // Instants go out as UTC (`...Z`); the hint below only labels the calendar for clients that use it.
  cal.x("X-WR-TIMEZONE", trip.timeZone);
  cal.ttl(3600); // X-PUBLISHED-TTL: PT1H
  const byDay = new Map<number, FeedStop[]>();
  for (const s of stops) byDay.set(s.day, [...(byDay.get(s.day) ?? []), s]);
  const dayHours = new Map(days.map((d) => [d.day, d]));
  const travelJson = JSON.stringify(legs.filter((l) => !l.error && typeof l.seconds === "number").map((l) => ({ from: l.fromStopId, to: l.toStopId, mode: l.mode.toLowerCase() === "walk" || l.mode.toLowerCase() === "walking" ? "walking" : "driving", seconds: l.seconds })));
  const dates = tripDates(trip);
  dates.forEach((date, day) => {
    const dayStops = (byDay.get(day) ?? []).slice().sort((a, b) => (a.order < b.order ? -1 : a.order > b.order ? 1 : 0));
    if (dayStops.length === 0) return;
    const entries = dayStops.map((s) => ({ id: s.id, name: s.name, kind: s.kind === "custom" ? "custom" : "place", durationMin: s.durationMin, fixedStart: s.fixedStart ?? null, modeToNext: s.modeToNext ?? null }));
    const hours = dayHours.get(day);
    const out = JSON.parse(engine.computeDay(date, hours?.start ?? trip.defaultDayStart ?? "09:00", hours?.end ?? trip.defaultDayEnd ?? "21:00", JSON.stringify(entries), travelJson, trip.defaultTravelMode ?? "driving")) as EngineDay;
    for (const e of out.entries) {
      const stop = dayStops.find((s) => s.id === e.id);
      if (!stop) continue;
      const event = cal.createEvent({
        id: `${stop.id}@${host}`,
        start: new Date(engine.epochSeconds(e.start, trip.timeZone) * 1000),
        end: new Date(engine.epochSeconds(e.end, trip.timeZone) * 1000),
        summary: stop.name,
        description: [stop.notes?.trim(), `${trip.name} · Day ${day + 1}`, `tripplanner://trip/${trip.id}/stop/${stop.id}`].filter(Boolean).join("\n"),
      });
      // Deterministic output (same data -> same bytes -> same ETag): stamp from the stop's last write,
      // SEQUENCE = seconds since 2020 so it grows with every edit and stays a 32-bit integer.
      const updated = stop.updatedAt ?? 0;
      event.stamp(new Date(updated));
      event.sequence(Math.max(0, Math.floor((updated - SEQUENCE_EPOCH_MS) / 1000)));
      if (stop.placeId) {
        event.location({ title: stop.name, address: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(stop.name)}&query_place_id=${encodeURIComponent(stop.placeId)}` });
      } else if (stop.address) {
        event.location({ title: stop.name, address: stop.address });
      }
    }
  });
  return cal;
}

/**
 * Complexity:
 * - Time: O(B) for the B-byte body.
 * - Space: O(1).
 */
export function etagOf(body: string): string {
  return `"${crypto.createHash("sha1").update(body).digest("base64url")}"`;
}

/**
 * Travel times between consecutive stops (design §8.3, §15). One `travel/{from}_{to}_{mode}`
 * document per adjacency and mode, written only by Functions; the client renders whatever is
 * there and shows a shimmer for missing legs. Pure planning here, Routes I/O at the bottom.
 */
export const MODES = ["DRIVE", "WALK"] as const;
export type Mode = (typeof MODES)[number];
export const CACHE_MS = 24 * 3600 * 1000;

/** A stop as travel sees it: the id of the event that contains it plus its coordinates. */
export interface StopPoint {
  id: string; // eventId
  lat: number;
  lng: number;
}

export interface Pair {
  from: StopPoint;
  to: StopPoint;
}

export interface LegDoc {
  fromEventId: string;
  toEventId: string;
  mode: Mode;
  seconds?: number;
  meters?: number;
  error?: string;
  /** epoch ms; absent or with `error` means "recompute". */
  computedAt?: number;
}

/**
 * Complexity:
 * - Time: O(1).
 * - Space: O(1).
 */
export function legId(fromEventId: string, toEventId: string, mode: Mode): string {
  return `${fromEventId}_${toEventId}_${mode}`;
}

/**
 * Consecutive pairs of a day's stop-bearing events, already sorted by `order`.
 *
 * Complexity:
 * - Time: O(S) for S stops.
 * - Space: O(S).
 */
export function adjacentPairs(stops: StopPoint[]): Pair[] {
  const pairs: Pair[] = [];
  for (let i = 1; i < stops.length; i++) pairs.push({ from: stops[i - 1], to: stops[i] });
  return pairs;
}

export interface LegPlan {
  /** Existing leg ids whose adjacency no longer exists on the affected days. */
  toDelete: string[];
  /** Pairs to (re)compute per mode: missing, stale, or previously failed. */
  toCompute: Record<Mode, Pair[]>;
}

/**
 * Decides what to delete and what to ask Routes for, given the affected days' current
 * adjacencies and the trip's existing legs. Only legs touching the affected events are judged,
 * so other days' legs are left alone (design §8.3: recompute affected adjacencies only).
 *
 * Complexity:
 * - Time: O(P + L) for P current pairs and L existing legs.
 * - Space: O(P + L).
 */
export function planLegs(pairs: Pair[], affectedEventIds: Set<string>, existing: Map<string, LegDoc>, now: number): LegPlan {
  const desired = new Set<string>();
  const toCompute: Record<Mode, Pair[]> = { DRIVE: [], WALK: [] };
  for (const p of pairs) {
    for (const mode of MODES) {
      const id = legId(p.from.id, p.to.id, mode);
      desired.add(id);
      const cur = existing.get(id);
      const fresh = cur && !cur.error && cur.computedAt !== undefined && now - cur.computedAt < CACHE_MS;
      if (!fresh) toCompute[mode].push(p);
    }
  }
  const toDelete: string[] = [];
  for (const [id, leg] of existing) {
    if (desired.has(id)) continue;
    if (affectedEventIds.has(leg.fromEventId) || affectedEventIds.has(leg.toEventId)) toDelete.push(id);
  }
  return { toDelete, toCompute };
}

/** One element of a computeRouteMatrix response (fields we request). */
export interface MatrixElement {
  originIndex: number;
  destinationIndex: number;
  status?: { code?: number; message?: string };
  condition?: string;
  distanceMeters?: number;
  duration?: string; // "1234s"
}

/**
 * Picks the diagonal (pair i = origin i -> destination i) out of a matrix response and turns
 * it into leg documents; a missing or failed element becomes an `error` leg so the client shows
 * a dash rather than a shimmer forever, and the next change retries it.
 *
 * Complexity:
 * - Time: O(P + E) for P pairs and E elements.
 * - Space: O(P).
 */
export function legsFromMatrix(pairs: Pair[], mode: Mode, elements: MatrixElement[], now: number): LegDoc[] {
  const byIndex = new Map<number, MatrixElement>();
  for (const e of elements) if (e.originIndex === e.destinationIndex) byIndex.set(e.originIndex, e);
  return pairs.map((p, i) => {
    const e = byIndex.get(i);
    const base = { fromEventId: p.from.id, toEventId: p.to.id, mode, computedAt: now };
    if (!e) return { ...base, error: "no route element" };
    if (e.status?.code) return { ...base, error: e.status.message ?? `status ${e.status.code}` };
    if (e.condition && e.condition !== "ROUTE_EXISTS") return { ...base, error: e.condition };
    const seconds = e.duration ? Math.round(parseFloat(e.duration.replace(/s$/, ""))) : NaN;
    if (!Number.isFinite(seconds)) return { ...base, error: "no duration" };
    return { ...base, seconds, meters: Math.round(e.distanceMeters ?? 0) };
  });
}

/**
 * Request body for computeRouteMatrix over the pairs' origins and destinations (the matrix
 * is P x P; only the diagonal is used - for the <= 3 pairs a stop change touches that is cheaper
 * than one computeRoutes call per pair, design §15).
 *
 * Complexity:
 * - Time: O(P).
 * - Space: O(P).
 */
export function matrixRequest(pairs: Pair[], mode: Mode): Record<string, unknown> {
  const wp = (s: StopPoint) => ({ waypoint: { location: { latLng: { latitude: s.lat, longitude: s.lng } } } });
  const body: Record<string, unknown> = {
    origins: pairs.map((p) => wp(p.from)),
    destinations: pairs.map((p) => wp(p.to)),
    travelMode: mode,
  };
  if (mode === "DRIVE") body.routingPreference = "TRAFFIC_UNAWARE"; // cacheable 24 h, cheapest SKU
  return body;
}

/**
 * computeRouteMatrix streams a JSON array, so an error body is `[{"error":{...}}]`; other
 * endpoints use a bare `{"error":{...}}`. Falls back to the HTTP status.
 *
 * Complexity:
 * - Time: O(B) to parse a B-byte body.
 * - Space: O(B).
 */
export function routesErrorMessage(status: number, body: string): string {
  const fallback = `Routes HTTP ${status}`;
  try {
    const parsed = JSON.parse(body) as unknown;
    const obj = (Array.isArray(parsed) ? parsed[0] : parsed) as { error?: { message?: string } } | undefined;
    return obj?.error?.message ?? fallback;
  } catch {
    return fallback;
  }
}

export const ROUTES_MATRIX_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix";
export const ROUTES_FIELD_MASK = "originIndex,destinationIndex,status,condition,distanceMeters,duration";

/**
 * One Routes call per mode. Throws on HTTP errors so the caller can record `error` legs.
 *
 * Complexity:
 * - Time: O(P^2) billed elements for P pairs; one network round trip.
 * - Space: O(P^2) response.
 */
export async function computeMatrix(apiKey: string, pairs: Pair[], mode: Mode, fetchImpl: typeof fetch = fetch): Promise<MatrixElement[]> {
  const res = await fetchImpl(ROUTES_MATRIX_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Goog-Api-Key": apiKey, "X-Goog-FieldMask": ROUTES_FIELD_MASK },
    body: JSON.stringify(matrixRequest(pairs, mode)),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(routesErrorMessage(res.status, text));
  }
  return JSON.parse(text) as MatrixElement[];
}

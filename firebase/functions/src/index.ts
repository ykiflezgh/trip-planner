/**
 * Cloud Functions (2nd gen) — skeletons for design §8.
 * Secrets: ROUTES_API_KEY and ANTHROPIC_API_KEY via Secret Manager
 * (`firebase functions:secrets:set ROUTES_API_KEY`), never in source.
 * App Check: set enforceAppCheck on every callable before launch (§11).
 */
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentWrittenWithAuthContext, type DocumentSnapshot } from "firebase-functions/v2/firestore";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { defineBoolean, defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions/v2";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore, type DocumentReference } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getFunctions } from "firebase-admin/functions";
import * as crypto from "node:crypto";
import { needsDigest, planNotification, tokensToPrune, type BurstWindow, type EventFacts } from "./burst";
import { buildMessage, type Notice } from "./notifications";
import { buildFeed, etagOf, newToken, tokenHash, tripDates, type FeedDay, type FeedLeg, type FeedStop, type FeedTrip } from "./feed";
import { callClaude, suggestOrder, type HttpFetch, type SuggestDay, type SuggestLeg, type SuggestStop } from "./suggest";
import { MODES, adjacentPairs, computeMatrix, legId, legsFromMatrix, planLegs, type LegDoc, type StopPoint } from "./travel";

initializeApp();
const db = getFirestore();
// Optional facts (stopId, fromDay) are omitted rather than written as undefined.
db.settings({ ignoreUndefinedProperties: true });

const ROUTES_API_KEY = defineSecret("ROUTES_API_KEY");
const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");
/** Kill switch for §8.7 (stands in for the Remote Config flag `suggest_order_enabled`). */
const SUGGEST_ORDER_ENABLED = defineBoolean("SUGGEST_ORDER_ENABLED", { default: true });
/**
 * Dev-only: also push to the actor's own devices. Design §10 excludes the actor; with a single
 * test account that would make the fan-out unobservable. Set in .env.<project> for dev only.
 */
const NOTIFY_ACTOR_DEBUG = defineBoolean("NOTIFY_ACTOR_DEBUG", {
  default: false,
  description: "Also notify the actor's own devices (single-account testing). Never true in prod.",
});

/**
 * §8.2 — owner creates a shareable invite code.
 *
 * Complexity:
 * - Time: O(1) single document read + O(1) random key generation + O(1) write.
 * - Space: O(1) auxiliary memory.
 */
export const createInvite = onCall({ enforceAppCheck: false /* TODO true */ }, async (req) => {
  const uid = req.auth?.uid;
  const tripId = req.data?.tripId as string;
  if (!uid || !tripId) throw new HttpsError("unauthenticated", "sign in first");

  const trip = await db.doc(`trips/${tripId}`).get();
  if (!trip.exists) throw new HttpsError("not-found", "This trip no longer exists.");
  if (trip.get("roles")?.[uid] !== "owner") throw new HttpsError("permission-denied", "Only the trip owner can share it.");

  const code = crypto.randomBytes(6).toString("base64url");
  await db.doc(`invites/${code}`).set({
    tripId,
    role: "editor",
    createdBy: uid,
    createdAt: FieldValue.serverTimestamp(),
    expiresAt: new Date(Date.now() + 7 * 24 * 3600 * 1000),
    maxUses: 20,
    uses: 0,
  });
  return { code };
});

/**
 * §8.2 — membership changes only happen here (Admin SDK bypasses rules by design).
 *
 * Complexity:
 * - Time: O(1) Firestore transaction read + two document writes (trip and invite).
 * - Space: O(1) auxiliary memory.
 */
export const redeemInvite = onCall(async (req) => {
  const uid = req.auth?.uid;
  const code = req.data?.code as string;
  if (!uid || !code) throw new HttpsError("unauthenticated", "sign in first");

  const result = await db.runTransaction(async (tx) => {
    const inviteRef = db.doc(`invites/${code}`);
    const invite = await tx.get(inviteRef);
    if (!invite.exists) throw new HttpsError("not-found", "This invite link isn't valid.");
    const tripId = invite.get("tripId") as string;
    const tripRef = db.doc(`trips/${tripId}`);
    const trip = await tx.get(tripRef);
    if (!trip.exists) throw new HttpsError("not-found", "This trip no longer exists.");

    // Already a member (including the owner re-opening their own link): nothing to change.
    if ((trip.get("memberIds") ?? []).includes(uid)) return { tripId, alreadyMember: true };

    if (invite.get("expiresAt").toDate() < new Date()) throw new HttpsError("failed-precondition", "This invite link has expired.");
    if (invite.get("uses") >= invite.get("maxUses")) throw new HttpsError("resource-exhausted", "This invite link has been used up.");

    tx.update(tripRef, {
      [`roles.${uid}`]: invite.get("role"),
      memberIds: FieldValue.arrayUnion(uid),
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.update(inviteRef, { uses: FieldValue.increment(1) });
    return { tripId, alreadyMember: false };
  });
  if (!result.alreadyMember) {
    await db.collection(`trips/${result.tripId}/activity`).add({
      type: "member_joined",
      actorId: uid,
      actorName: await displayName(uid),
      stopId: null,
      stopName: "",
      day: 0,
      summary: "member joined",
      createdAt: FieldValue.serverTimestamp(),
    });
  }
  return result;
});

/**
 * `users/{uid}.displayName`, or "" when the profile is missing.
 *
 * Complexity:
 * - Time: O(1) document read.
 * - Space: O(1).
 */
async function displayName(uid: string): Promise<string> {
  if (!uid) return "";
  const u = await db.doc(`users/${uid}`).get();
  return (u.get("displayName") as string | undefined) ?? "";
}

/** Fields whose change alone is not a user-visible edit. */
const BOOKKEEPING_FIELDS = new Set(["updatedAt", "updatedBy", "placeFetchedAt", "addedAt"]);

/**
 * Classifies a stop write. `null` when nothing a member would care about changed.
 *
 * Complexity:
 * - Time: O(F) over the F fields of the document.
 * - Space: O(F).
 */
export function classifyStopWrite(before: DocumentSnapshot | undefined, after: DocumentSnapshot | undefined): EventFacts | null {
  const b = before?.exists ? before.data() ?? {} : undefined;
  const a = after?.exists ? after.data() ?? {} : undefined;
  const stopName = String(a?.name ?? b?.name ?? "");
  if (!b && a) return { type: "stop_added", stopName, day: Number(a.day ?? 0) };
  if (b && !a) return { type: "stop_removed", stopName, day: Number(b.day ?? 0) };
  if (!b || !a) return null;
  if (a.day !== b.day || a.order !== b.order) {
    // A pin that also reorders (design v1.1 §8.4 step 2) reads as "moved to 14:00", not "reordered".
    if ((a.fixedStart ?? null) !== (b.fixedStart ?? null) && a.fixedStart) return { type: "entry_pinned", stopName, day: Number(a.day ?? 0) };
    return { type: "stop_moved", stopName, day: Number(a.day ?? 0), fromDay: Number(b.day ?? 0) };
  }
  if ((a.fixedStart ?? null) !== (b.fixedStart ?? null)) return { type: a.fixedStart ? "entry_pinned" : "entry_unpinned", stopName, day: Number(a.day ?? 0) };
  if (a.durationMin !== b.durationMin) return { type: "entry_resized", stopName, day: Number(a.day ?? 0) };
  const changed = new Set([...Object.keys(a), ...Object.keys(b)]).values();
  for (const f of changed) {
    if (BOOKKEEPING_FIELDS.has(f)) continue;
    if (JSON.stringify(a[f]) !== JSON.stringify(b[f])) return { type: "stop_edited", stopName, day: Number(a.day ?? 0) };
  }
  return null;
}

/**
 * §8.1 + §8.3 — activity event per stop write; the fan-out runs on the event (§10).
 * The actor comes from the write's auth context (a Firebase Auth user's client-SDK write
 * arrives as authType "api_key" with the uid in authId - observed on the dev project; "unknown"
 * is accepted too); Admin/system writes fall back to the document's updatedBy/addedBy so the
 * feed still names someone.
 *
 * Complexity:
 * - Time: O(F) to diff the F fields + two document reads + one write.
 * - Space: O(F).
 */
export const onStopWritten = onDocumentWrittenWithAuthContext(
  { document: "trips/{tripId}/stops/{stopId}", secrets: [ROUTES_API_KEY] },
  async (event) => {
    const { tripId, stopId } = event.params;
    const before = event.data?.before;
    const after = event.data?.after;
    const facts = classifyStopWrite(before, after);
    if (!facts) return;
    facts.stopId = stopId;

    const fromDoc = String(after?.get("updatedBy") || after?.get("addedBy") || before?.get("updatedBy") || before?.get("addedBy") || "");
    const userWrite = event.authType === "api_key" || event.authType === "unknown";
    const actorId = userWrite && event.authId ? event.authId : fromDoc;
    logger.debug("stop write", { tripId, stopId, type: facts.type, authType: event.authType, authId: event.authId, actorId });

    // 1. Activity event (feed + fan-out source, design §7)
    const day = facts.day;
    await db.collection(`trips/${tripId}/activity`).add({
      type: facts.type,
      actorId,
      actorName: await displayName(actorId),
      stopId,
      stopName: facts.stopName,
      day,
      fixedStart: (after?.get("fixedStart") as string | undefined) ?? null,
      fromDay: facts.fromDay ?? null,
      summary: `${facts.type.replace("_", " ")}: ${facts.stopName} (day ${day + 1})`,
      createdAt: FieldValue.serverTimestamp(),
    });

    // 2. Travel-time recompute for affected adjacencies (§8.3); pins and resizes call no Routes API,
    // and custom entries never take part in travel (design v1.1 §8.3).
    const isCustom = (after?.get("kind") ?? before?.get("kind")) === "custom";
    if (!isCustom && (facts.type === "stop_added" || facts.type === "stop_removed" || facts.type === "stop_moved")) {
      const days = new Set<number>([facts.day]);
      if (facts.fromDay !== undefined) days.add(facts.fromDay);
      await recomputeTravel(tripId, stopId, [...days]);
    }
  },
);

/**
 * §8.3 — legs for the affected days: current adjacencies vs. stored legs, then at most one
 * Routes call per mode (two per stop change, design §15 asks for fewer than five). Legs whose
 * adjacency disappeared are deleted; fresh ones (< 24 h) are kept.
 *
 * Complexity:
 * - Time: O(S log S + L) to order the S stops of the affected days and scan L stored legs,
 *   plus O(P^2) billed matrix elements for the P pairs to compute (P <= 3 per change).
 * - Space: O(S + L).
 */
async function recomputeTravel(tripId: string, changedStopId: string, days: number[]): Promise<void> {
  const stopsSnap = await db.collection(`trips/${tripId}/stops`).where("day", "in", days).get();
  const byDay = new Map<number, StopPoint[]>();
  const affected = new Set<string>([changedStopId]);
  // Fractional-index keys sort by plain code-point order (core/util/FractionalIndex), not locale order.
  const byOrder = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);
  for (const d of stopsSnap.docs.sort((a, b) => byOrder(String(a.get("order")), String(b.get("order"))))) {
    if (d.get("kind") === "custom") continue; // adjacency skips custom entries (design v1.1 §6.5 rule 2)
    if (typeof d.get("lat") !== "number" || typeof d.get("lng") !== "number") continue; // no place, nothing to route
    const point: StopPoint = { id: d.id, lat: Number(d.get("lat")), lng: Number(d.get("lng")) };
    const day = Number(d.get("day"));
    byDay.set(day, [...(byDay.get(day) ?? []), point]);
    affected.add(d.id);
  }
  const pairs = [...byDay.values()].flatMap(adjacentPairs);

  const travelRef = db.collection(`trips/${tripId}/travel`);
  const existing = new Map<string, LegDoc>();
  for (const d of (await travelRef.get()).docs) existing.set(d.id, d.data() as LegDoc);
  const now = Date.now();
  const plan = planLegs(pairs, affected, existing, now);

  const batch = db.batch();
  for (const id of plan.toDelete) batch.delete(travelRef.doc(id));
  let calls = 0;
  const apiKey = ROUTES_API_KEY.value();
  for (const mode of MODES) {
    const todo = plan.toCompute[mode];
    if (todo.length === 0) continue;
    let legs: LegDoc[];
    try {
      calls++;
      legs = legsFromMatrix(todo, mode, await computeMatrix(apiKey, todo, mode), now);
    } catch (e) {
      const msg = String((e as Error).message ?? e);
      logger.warn("routes call failed", { tripId, mode, pairs: todo.length, error: msg });
      legs = todo.map((p) => ({ fromStopId: p.from.id, toStopId: p.to.id, mode, error: msg, computedAt: now }));
    }
    for (const leg of legs) batch.set(travelRef.doc(legId(leg.fromStopId, leg.toStopId, leg.mode)), leg);
  }
  await batch.commit();
  logger.info("travel", { tripId, days, pairs: pairs.length, deleted: plan.toDelete.length, computed: plan.toCompute.DRIVE.length + plan.toCompute.WALK.length, routesCalls: calls });
}

interface Recipient {
  ref: DocumentReference;
  tokens: string[];
}

/**
 * Members who should hear about [actorId]'s change in [tripId]: everyone else with push on and
 * the trip not muted (design §10).
 *
 * Complexity:
 * - Time: O(M) for M members (one batched getAll).
 * - Space: O(T) for their T tokens.
 */
async function recipients(tripId: string, actorId: string): Promise<{ tripName: string; list: Recipient[] }> {
  const trip = await db.doc(`trips/${tripId}`).get();
  const tripName = String(trip.get("name") ?? "");
  const memberIds: string[] = (trip.get("memberIds") ?? []).filter((m: string) => m !== actorId || NOTIFY_ACTOR_DEBUG.value());
  if (memberIds.length === 0) return { tripName, list: [] };
  const users = await db.getAll(...memberIds.map((uid) => db.doc(`users/${uid}`)));
  const list: Recipient[] = [];
  for (const u of users) {
    const prefs = (u.get("notificationPrefs") as { push?: boolean; mutedTripIds?: string[] } | undefined) ?? {};
    if (prefs.push === false || (prefs.mutedTripIds ?? []).includes(tripId)) continue;
    const tokens = ((u.get("fcmTokens") as string[] | undefined) ?? []).filter((t) => typeof t === "string" && t.length > 0);
    if (tokens.length > 0) list.push({ ref: u.ref, tokens });
  }
  return { tripName, list };
}

/**
 * Sends one notice to every recipient device and prunes tokens FCM reports dead (design §10).
 *
 * Complexity:
 * - Time: O(T) for T tokens, in multicast chunks of 500.
 * - Space: O(T).
 */
async function send(notice: Omit<Notice, "tripName">, tripName: string, list: Recipient[]): Promise<void> {
  const owners = new Map<string, Recipient>();
  const tokens: string[] = [];
  for (const r of list) for (const t of r.tokens) { owners.set(t, r); tokens.push(t); }
  if (tokens.length === 0) { logger.info("no recipients", { tripId: notice.tripId, kind: notice.kind }); return; }

  const dead: string[] = [];
  let sent = 0;
  for (let i = 0; i < tokens.length; i += 500) {
    const chunk = tokens.slice(i, i + 500);
    const res = await getMessaging().sendEachForMulticast(buildMessage({ ...notice, tripName }, chunk));
    sent += res.successCount;
    dead.push(...tokensToPrune(chunk, res.responses.map((r) => ({ success: r.success, error: r.error ? { code: r.error.code, message: r.error.message } : undefined }))));
    res.responses.forEach((r, j) => { if (!r.success) logger.warn("push failed", { code: r.error?.code, message: r.error?.message, token: chunk[j].slice(0, 12) }); });
  }
  logger.info("push", { tripId: notice.tripId, kind: notice.kind, count: notice.count, devices: tokens.length, sent, pruned: dead.length });

  const byUser = new Map<string, string[]>();
  for (const t of dead) {
    const r = owners.get(t)!;
    byUser.set(r.ref.path, [...(byUser.get(r.ref.path) ?? []), t]);
  }
  await Promise.all([...byUser.entries()].map(([path, ts]) => db.doc(path).update({ fcmTokens: FieldValue.arrayRemove(...ts) })));
}

/**
 * Digest for a closed window ("A made N changes"), then the window document is gone.
 *
 * Complexity:
 * - Time: O(M + T) recipients + send.
 * - Space: O(T).
 */
async function sendDigest(tripId: string, actorId: string, actorName: string, window: BurstWindow): Promise<void> {
  const { tripName, list } = await recipients(tripId, actorId);
  await send({ kind: "digest", tripId, actorId, actorName, count: window.count, facts: window.latest }, tripName, list);
}

/**
 * §10 — fan-out on activity creation with burst collapsing. The per-(trip, actor) window lives
 * at trips/{tripId}/notify/{actorId} (no client rules -> unreadable); planNotification decides
 * whether this event is pushed now or folded into the digest the flush task sends.
 *
 * Complexity:
 * - Time: O(M + T) for M members and T tokens, plus one transaction on the window doc.
 * - Space: O(T).
 */
export const onActivityCreated = onDocumentCreated("trips/{tripId}/activity/{eventId}", async (event) => {
  const snap = event.data;
  if (!snap) return;
  const { tripId } = event.params;
  const actorId = String(snap.get("actorId") ?? "");
  const actorName = String(snap.get("actorName") ?? "");
  const facts: EventFacts = { type: String(snap.get("type") ?? ""), stopName: String(snap.get("stopName") ?? ""), day: Number(snap.get("day") ?? 0) };
  const stopId = snap.get("stopId") as string | null | undefined;
  if (stopId) facts.stopId = stopId;
  const fromDay = snap.get("fromDay") as number | null | undefined;
  if (typeof fromDay === "number") facts.fromDay = fromDay;
  const fixedStart = snap.get("fixedStart") as string | null | undefined;
  if (fixedStart) facts.fixedStart = fixedStart;
  if (!actorId) { logger.warn("activity without actor; no fan-out", { tripId, eventId: event.params.eventId }); return; }

  const windowRef = db.doc(`trips/${tripId}/notify/${actorId}`);
  const now = Date.now();
  const decision = await db.runTransaction(async (tx) => {
    const cur = await tx.get(windowRef);
    const d = planNotification(cur.exists ? (cur.data() as BurstWindow) : undefined, now, facts);
    tx.set(windowRef, d.window);
    return d;
  });

  if (decision.flushStale) await sendDigest(tripId, actorId, actorName, decision.flushStale);

  if (decision.action === "send_single") {
    const { tripName, list } = await recipients(tripId, actorId);
    await send({ kind: "single", tripId, actorId, actorName, count: decision.window.count, facts }, tripName, list);
  } else {
    logger.info("deferred to digest", { tripId, actorId, count: decision.window.count });
  }

  if (decision.scheduleFlush) {
    try {
      await getFunctions().taskQueue("flushNotifyDigest").enqueue(
        { tripId, actorId, actorName, openedAt: decision.window.openedAt },
        { scheduleTime: new Date(decision.window.expiresAt) },
      );
      await windowRef.update({ flushScheduled: true });
      logger.info("flush scheduled", { tripId, actorId, at: new Date(decision.window.expiresAt).toISOString() });
    } catch (e) {
      // Retried on the next deferred event; an expired unflushed window is sent inline later.
      logger.error("could not enqueue flush task", { tripId, actorId, error: String(e) });
    }
  }
});

/**
 * §10 — end-of-window flush (Cloud Tasks). Sends the digest when the window reached three or
 * more events, otherwise every event already went out as a single. A window re-opened since
 * the task was scheduled (different openedAt) is left alone: its own task will follow.
 *
 * Complexity:
 * - Time: O(M + T) recipients + send.
 * - Space: O(T).
 */
export const flushNotifyDigest = onTaskDispatched<{ tripId: string; actorId: string; actorName: string; openedAt: number }>(
  { retryConfig: { maxAttempts: 3, minBackoffSeconds: 10 }, rateLimits: { maxConcurrentDispatches: 10 } },
  async (req) => {
    const { tripId, actorId, actorName, openedAt } = req.data;
    const windowRef = db.doc(`trips/${tripId}/notify/${actorId}`);
    const cur = await windowRef.get();
    if (!cur.exists) { logger.info("flush: window gone", { tripId, actorId }); return; }
    const window = cur.data() as BurstWindow;
    if (window.openedAt !== openedAt) { logger.info("flush: window superseded", { tripId, actorId }); return; }
    if (needsDigest(window)) await sendDigest(tripId, actorId, actorName, window);
    else logger.info("flush: nothing to digest", { tripId, actorId, count: window.count });
    await windowRef.delete();
  },
);

/**
 * §8.7 — Claude proposes an order for one day; validated and scored by the schedule engine
 * (see suggest.ts). Editors only; the client previews the result and applies it as new order keys.
 *
 * Complexity:
 * - Time: O(S + L) per attempt (at most 2) over the day's S stops and L legs, plus the reads.
 * - Space: O(S + L).
 */
export const suggestDayOrder = onCall({ secrets: [ANTHROPIC_API_KEY], timeoutSeconds: 60 }, async (req) => {
  const uid = req.auth?.uid;
  const tripId = String(req.data?.tripId ?? "");
  const day = Number(req.data?.day);
  if (!uid || !tripId || !Number.isInteger(day) || day < 0) throw new HttpsError("invalid-argument", "tripId and day are required");
  if (!SUGGEST_ORDER_ENABLED.value()) throw new HttpsError("unavailable", "Suggestions are switched off right now");
  const trip = await db.doc(`trips/${tripId}`).get();
  const role = trip.get("roles")?.[uid];
  if (!trip.exists || (role !== "owner" && role !== "editor")) throw new HttpsError("permission-denied", "Only editors can ask for suggestions");
  const apiKey = ANTHROPIC_API_KEY.value();
  if (!apiKey || apiKey.startsWith("placeholder")) throw new HttpsError("failed-precondition", "Suggestions are not configured on this server yet");

  const [stopsSnap, travelSnap, hoursDoc] = await Promise.all([
    db.collection(`trips/${tripId}/stops`).where("day", "==", day).get(),
    db.collection(`trips/${tripId}/travel`).get(),
    db.doc(`trips/${tripId}/days/${day}`).get(),
  ]);
  const stops: SuggestStop[] = stopsSnap.docs
    .map((d) => ({ id: d.id, name: String(d.get("name") ?? ""), kind: d.get("kind"), order: String(d.get("order") ?? ""), durationMin: Number(d.get("durationMin") ?? 60), fixedStart: d.get("fixedStart") || undefined, modeToNext: d.get("modeToNext") || undefined, notes: d.get("notes") || undefined }))
    .sort((a, b) => (a.order < b.order ? -1 : a.order > b.order ? 1 : 0));
  if (stops.length < 2) throw new HttpsError("failed-precondition", "Add at least two stops to this day first");
  const ids = new Set(stops.map((s) => s.id));
  const legs: SuggestLeg[] = travelSnap.docs.map((d) => d.data() as SuggestLeg).filter((l) => ids.has(l.fromStopId) && ids.has(l.toStopId));
  const date = tripDates({ id: tripId, name: "", startDate: String(trip.get("startDate") ?? ""), endDate: String(trip.get("endDate") ?? ""), timeZone: "UTC" })[day];
  if (!date) throw new HttpsError("invalid-argument", "That day is outside the trip");
  const dayIn: SuggestDay = {
    date, start: hoursDoc.get("start") ?? trip.get("defaultDayStart") ?? "09:00", end: hoursDoc.get("end") ?? trip.get("defaultDayEnd") ?? "21:00",
    defaultMode: String(trip.get("defaultTravelMode") ?? "driving").toLowerCase(),
  };
  try {
    const out = await suggestOrder(dayIn, stops, legs, (prompt) => callClaude(apiKey, prompt, fetch as unknown as HttpFetch));
    logger.info("suggestDayOrder", { tripId, day, stops: stops.length, lateMinutes: out.lateMinutes });
    return { orderedStopIds: out.orderedStopIds, rationale: out.rationale, warnings: out.warnings };
  } catch (e) {
    logger.warn("suggestDayOrder failed", { tripId, day, error: String(e) });
    throw new HttpsError("unavailable", (e as Error).message ?? "Could not get a suggestion");
  }
});

/** Hosting domain that serves /cal/** (design §8.6); mirrors AppConfig.appLinkHost on the clients. */
const FEED_HOST = process.env.APP_LINK_HOST ?? "tripplanner-dev-fe0a4.web.app";
const FEED_MAX_AGE_S = 900;
const FEED_MIN_INTERVAL_MS = 30_000;

/**
 * §8.6 — a member mints a private feed link for one trip. The token is returned once; only its
 * hash is stored, so a lost link is replaced by creating a new one.
 *
 * Complexity:
 * - Time: O(1) read + O(1) write.
 * - Space: O(1).
 */
export const createCalendarFeed = onCall(async (req) => {
  const uid = req.auth?.uid;
  const tripId = String(req.data?.tripId ?? "");
  if (!uid || !tripId) throw new HttpsError("unauthenticated", "sign in first");
  const trip = await db.doc(`trips/${tripId}`).get();
  if (!trip.exists || !(trip.get("memberIds") ?? []).includes(uid)) throw new HttpsError("permission-denied", "not a member");
  const token = newToken();
  await db.doc(`calendarFeeds/${tokenHash(token)}`).set({ tripId, uid, createdAt: FieldValue.serverTimestamp(), lastFetchedAt: null, revokedAt: null });
  return { url: `https://${FEED_HOST}/cal/${token}.ics` };
});

/**
 * §8.6 — revokes every feed the caller created for a trip.
 *
 * Complexity:
 * - Time: O(F) for the caller's F feeds on the trip.
 * - Space: O(F).
 */
export const revokeCalendarFeed = onCall(async (req) => {
  const uid = req.auth?.uid;
  const tripId = String(req.data?.tripId ?? "");
  if (!uid || !tripId) throw new HttpsError("unauthenticated", "sign in first");
  const feeds = await db.collection("calendarFeeds").where("uid", "==", uid).where("tripId", "==", tripId).where("revokedAt", "==", null).get();
  const batch = db.batch();
  feeds.docs.forEach((d) => batch.update(d.ref, { revokedAt: FieldValue.serverTimestamp() }));
  await batch.commit();
  return { revoked: feeds.size };
});

/**
 * §8.6 — `GET /cal/{token}.ics` via the Hosting rewrite. 404 unless the feed exists, is not
 * revoked and its owner is still a member. Runs the schedule engine's JS build, answers with
 * an ETag (304 on match), private caching and a publish TTL; logs `lastFetchedAt` and refuses
 * fetches closer than 30 s apart on the same token.
 *
 * Complexity:
 * - Time: O(D · S) engine work plus the reads (trip, days, stops, travel).
 * - Space: O(S).
 */
export const calendarFeed = onRequest({ cors: false }, async (req, res) => {
  const match = /\/cal\/([A-Za-z0-9_-]{20,})\.ics$/.exec(req.path);
  if (req.method !== "GET" && req.method !== "HEAD") { res.status(405).end(); return; }
  if (!match) { res.status(404).end(); return; }
  const feedRef = db.doc(`calendarFeeds/${tokenHash(match[1])}`);
  const feed = await feedRef.get();
  if (!feed.exists || feed.get("revokedAt")) { res.status(404).end(); return; }
  const last = feed.get("lastFetchedAt")?.toMillis?.() ?? 0;
  if (Date.now() - last < FEED_MIN_INTERVAL_MS) { res.status(429).set("Retry-After", "30").end(); return; }
  const tripId = String(feed.get("tripId")), uid = String(feed.get("uid"));
  const tripDoc = await db.doc(`trips/${tripId}`).get();
  if (!tripDoc.exists || !(tripDoc.get("memberIds") ?? []).includes(uid)) { res.status(404).end(); return; }

  const [stopsSnap, travelSnap, daysSnap] = await Promise.all([
    db.collection(`trips/${tripId}/stops`).get(), db.collection(`trips/${tripId}/travel`).get(), db.collection(`trips/${tripId}/days`).get(),
  ]);
  const t = tripDoc.data()!;
  const trip: FeedTrip = { id: tripId, name: String(t.name ?? ""), startDate: String(t.startDate ?? ""), endDate: String(t.endDate ?? ""), timeZone: String(t.timeZone ?? "UTC"), defaultDayStart: t.defaultDayStart, defaultDayEnd: t.defaultDayEnd, defaultTravelMode: t.defaultTravelMode };
  const stops: FeedStop[] = stopsSnap.docs.map((d) => ({ id: d.id, name: String(d.get("name") ?? ""), kind: d.get("kind"), placeId: d.get("placeId") || undefined, address: d.get("address") || undefined, day: Number(d.get("day") ?? 0), order: String(d.get("order") ?? ""), durationMin: Number(d.get("durationMin") ?? 60), fixedStart: d.get("fixedStart") || undefined, modeToNext: d.get("modeToNext") || undefined, notes: d.get("notes") || undefined, updatedAt: d.get("updatedAt")?.toMillis?.() }));
  const legs: FeedLeg[] = travelSnap.docs.map((d) => d.data() as FeedLeg);
  const days: FeedDay[] = daysSnap.docs.map((d) => ({ day: Number(d.id), start: d.get("start"), end: d.get("end") }));

  const body = buildFeed(trip, stops, legs, days, FEED_HOST).toString();
  const etag = etagOf(body);
  void feedRef.update({ lastFetchedAt: FieldValue.serverTimestamp() }).catch(() => undefined);
  res.set("ETag", etag).set("Cache-Control", `private, max-age=${FEED_MAX_AGE_S}`).set("Content-Type", "text/calendar; charset=utf-8");
  if (req.headers["if-none-match"] === etag) { res.status(304).end(); return; }
  res.status(200).send(req.method === "HEAD" ? "" : body);
});

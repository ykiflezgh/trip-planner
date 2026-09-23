/**
 * Cloud Functions (2nd gen) — skeletons for design §8.
 * Secrets: ROUTES_API_KEY and GEMINI_API_KEY via Secret Manager
 * (`firebase functions:secrets:set ROUTES_API_KEY`), never in source.
 * App Check: set enforceAppCheck on every callable before launch (§11).
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { defineSecret } from "firebase-functions/params";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import * as crypto from "node:crypto";

initializeApp();
const db = getFirestore();

const ROUTES_API_KEY = defineSecret("ROUTES_API_KEY");
const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");

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

  return db.runTransaction(async (tx) => {
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
});

/**
 * §8.1 + §8.3 — activity event, travel-time recompute, FCM fan-out.
 *
 * Complexity:
 * - Time: O(M + T) where M is the number of members in the trip (fetching each member doc)
 *   and T is the total number of FCM device tokens across all members for multicast messaging.
 * - Space: O(T) auxiliary memory to collect device registration tokens.
 */
export const onStopWritten = onDocumentWritten(
  { document: "trips/{tripId}/stops/{stopId}", secrets: [ROUTES_API_KEY] },
  async (event) => {
    const { tripId, stopId } = event.params;
    const before = event.data?.before;
    const after = event.data?.after;
    const type = !before?.exists ? "stop_added" : !after?.exists ? "stop_removed" : "stop_moved";
    const actorId = (after?.get("addedBy") as string) ?? "unknown";
    const name = (after?.get("name") ?? before?.get("name") ?? "a stop") as string;
    const day = (after?.get("day") ?? before?.get("day") ?? 0) as number;

    // 1. Activity event (drives the feed and the fan-out below)
    await db.collection(`trips/${tripId}/activity`).add({
      type,
      actorId,
      stopId,
      summary: `${type.replace("_", " ")}: ${name} (day ${day + 1})`,
      createdAt: FieldValue.serverTimestamp(),
    });

    // 2. Travel-time recompute for affected adjacencies (§8.3)
    // TODO Phase 2: load the day's stops ordered by `order`, find neighbours of
    // stopId, call Routes API computeRouteMatrix with ROUTES_API_KEY.value(),
    // honor a 24h cache in trips/{tripId}/travel, delete legs that no longer exist.

    // 3. Fan-out (§10) — data message so the client localizes and deep-links
    const trip = await db.doc(`trips/${tripId}`).get();
    const memberIds: string[] = (trip.get("memberIds") ?? []).filter((m: string) => m !== actorId);
    const tokens: string[] = [];
    for (const uid of memberIds) {
      const u = await db.doc(`users/${uid}`).get();
      tokens.push(...((u.get("fcmTokens") as string[] | undefined) ?? []));
    }
    // TODO Phase 2: burst collapsing (3+ events / same actor / 60s => digest, §10)
    if (tokens.length > 0) {
      const res = await getMessaging().sendEachForMulticast({
        tokens,
        data: { tripId, stopId, type },
      });
      // TODO prune tokens where res.responses[i].error?.code === "messaging/registration-token-not-registered"
      void res;
    }
  },
);

/**
 * §8.5 — Gemini proposes an order for one day; validated before returning.
 *
 * Complexity:
 * - Time: O(K) where K is the number of stops on the target day (fetching stops collection and mapping IDs).
 * - Space: O(K) auxiliary space to collect stop IDs.
 */
export const suggestDayOrder = onCall({ secrets: [GEMINI_API_KEY] }, async (req) => {
  const uid = req.auth?.uid;
  const { tripId, day } = req.data ?? {};
  if (!uid) throw new HttpsError("unauthenticated", "sign in first");

  const trip = await db.doc(`trips/${tripId}`).get();
  if (!(trip.get("memberIds") ?? []).includes(uid)) throw new HttpsError("permission-denied", "not a member");

  const stops = await db
    .collection(`trips/${tripId}/stops`)
    .where("day", "==", day)
    .get();
  const ids = stops.docs.map((d) => d.id);

  // TODO Phase 3: call Gemini (generateContent with responseSchema =
  // { orderedStopIds: string[], rationale: string }) using GEMINI_API_KEY.value(),
  // include the travel matrix in the prompt, then validate that orderedStopIds is a
  // permutation of `ids` before returning. Behind Remote Config flag suggest_order_enabled.
  return { orderedStopIds: ids, rationale: "placeholder: Gemini call not wired yet" };
});

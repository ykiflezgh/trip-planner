/**
 * FCM message shapes (design §10). Android gets a pure data message and words it on the device
 * (shared/feature/notifications/NotificationText.kt). iOS cannot show a data-only push while
 * backgrounded, so the same facts ride along as an APNs alert with localization keys resolved
 * from the app's Localizable.strings — still "the client renders localized text".
 */
import type { MulticastMessage } from "firebase-admin/messaging";
import type { EventFacts } from "./burst";

export type Kind = "single" | "digest";

export interface Notice {
  kind: Kind;
  tripId: string;
  tripName: string;
  actorId: string;
  actorName: string;
  count: number;
  facts: EventFacts;
}

/**
 * Complexity:
 * - Time: O(1).
 * - Space: O(1).
 */
export function collapseKey(tripId: string, actorId: string): string {
  return `${tripId}:${actorId}`;
}

/**
 * iOS alert body as a loc-key + args (keys live in iosApp/en.lproj/Localizable.strings).
 *
 * Complexity:
 * - Time: O(1).
 * - Space: O(1).
 */
export function iosAlert(n: Notice): { "loc-key": string; "loc-args": string[] } {
  const who = n.actorName || "Someone";
  const stop = n.facts.stopName || "a stop";
  const day = String(n.facts.day + 1);
  if (n.kind === "digest") return { "loc-key": "notif_digest", "loc-args": [who, String(n.count)] };
  switch (n.facts.type) {
    case "stop_added":
      return { "loc-key": "notif_stop_added", "loc-args": [who, stop, day] };
    case "stop_removed":
      return { "loc-key": "notif_stop_removed", "loc-args": [who, stop, day] };
    case "stop_moved":
      return n.facts.fromDay !== undefined && n.facts.fromDay !== n.facts.day
        ? { "loc-key": "notif_stop_moved_day", "loc-args": [who, stop, String(n.facts.fromDay + 1), day] }
        : { "loc-key": "notif_stop_reordered", "loc-args": [who, stop, day] };
    case "stop_edited":
      return { "loc-key": "notif_stop_edited", "loc-args": [who, stop] };
    case "member_joined":
      return { "loc-key": "notif_member_joined", "loc-args": [who] };
    case "entry_pinned":
      return { "loc-key": "notif_entry_pinned", "loc-args": [who, stop, n.facts.fixedStart ?? "", day] };
    case "entry_unpinned":
      return { "loc-key": "notif_entry_unpinned", "loc-args": [who, stop] };
    case "entry_resized":
      return { "loc-key": "notif_entry_resized", "loc-args": [who, stop] };
    default:
      return { "loc-key": "notif_generic", "loc-args": [who] };
  }
}

/**
 * One multicast message for up to 500 tokens. Data values must be strings (FCM constraint).
 *
 * Complexity:
 * - Time: O(T) to attach T tokens.
 * - Space: O(T).
 */
export function buildMessage(n: Notice, tokens: string[]): MulticastMessage {
  const key = collapseKey(n.tripId, n.actorId);
  const data: Record<string, string> = {
    kind: n.kind,
    tripId: n.tripId,
    tripName: n.tripName,
    actorId: n.actorId,
    actorName: n.actorName,
    type: n.facts.type,
    stopId: n.facts.stopId ?? "",
    stopName: n.facts.stopName,
    day: String(n.facts.day),
    count: String(n.count),
  };
  if (n.facts.fromDay !== undefined) data.fromDay = String(n.facts.fromDay);
  if (n.facts.fixedStart) data.fixedStart = n.facts.fixedStart;
  return {
    tokens,
    data,
    android: { priority: "high", collapseKey: key },
    apns: {
      headers: { "apns-collapse-id": key, "apns-priority": "10" },
      payload: {
        aps: {
          alert: { title: n.tripName || "Trip Planner", ...iosAlert(n) },
          sound: "default",
          threadId: n.tripId,
        },
      },
    },
  };
}

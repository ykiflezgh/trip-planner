/**
 * Burst collapsing (design §10): three or more events from the same actor within 60 s become
 * one digest. Pure decisions over a per-(trip, actor) window document so they are unit-testable
 * without Firestore; index.ts persists the window and sends.
 *
 * Timeline for a burst of N events from one actor:
 *   event 1, 2  -> pushed at once as singles (low latency for the common one-off edit)
 *   event 3..N  -> not pushed; a flush task is scheduled for the window's end
 *   window end  -> one digest "A made N changes" that replaces the singles in the tray
 *                  (same collapse key / notification id on the client)
 * So a burst costs at most three pushes per recipient device, however long it runs.
 */
export const WINDOW_MS = 60_000;
/** Events pushed individually before a window turns into a digest. */
export const IMMEDIATE_LIMIT = 2;

/** What the client needs to word one event; mirrors PushKeys in shared/. */
export interface EventFacts {
  type: string;
  stopId?: string;
  stopName: string;
  day: number;
  fromDay?: number;
  /** Pinned "HH:mm" for entry_pinned events (design v1.1 §8.4). */
  fixedStart?: string;
}

export interface BurstWindow {
  openedAt: number; // epoch ms
  expiresAt: number;
  count: number;
  /** A flush task for this window was enqueued successfully. */
  flushScheduled: boolean;
  latest: EventFacts;
}

export interface Decision {
  window: BurstWindow;
  /** Push this event now as a single, or hold it for the digest. */
  action: "send_single" | "defer";
  /** Enqueue the end-of-window flush (first deferral, or a retry after a failed enqueue). */
  scheduleFlush: boolean;
  /** A previous window that reached digest size but never got a flush task: send it now. */
  flushStale?: BurstWindow;
}

/**
 * Complexity:
 * - Time: O(1).
 * - Space: O(1).
 */
export function planNotification(existing: BurstWindow | undefined, now: number, event: EventFacts): Decision {
  if (!existing || existing.expiresAt <= now) {
    const window: BurstWindow = { openedAt: now, expiresAt: now + WINDOW_MS, count: 1, flushScheduled: false, latest: event };
    const stale = existing && existing.count > IMMEDIATE_LIMIT && !existing.flushScheduled ? existing : undefined;
    return { window, action: "send_single", scheduleFlush: false, flushStale: stale };
  }
  const window: BurstWindow = { ...existing, count: existing.count + 1, latest: event };
  if (window.count <= IMMEDIATE_LIMIT) return { window, action: "send_single", scheduleFlush: false };
  return { window, action: "defer", scheduleFlush: !window.flushScheduled };
}

/**
 * True when the window should be sent as a digest at flush time (otherwise every event was
 * already pushed individually and there is nothing to add).
 *
 * Complexity:
 * - Time: O(1).
 * - Space: O(1).
 */
export function needsDigest(window: BurstWindow): boolean {
  return window.count > IMMEDIATE_LIMIT;
}

export interface SendOutcome {
  success: boolean;
  error?: { code: string; message?: string };
}

/**
 * Tokens FCM reported as dead (design §10: prune on UNREGISTERED). `invalid-argument` is only
 * treated as a dead token when FCM says so explicitly, since the same code is returned for a
 * malformed payload, where pruning would wipe every device.
 *
 * Complexity:
 * - Time: O(T) for T tokens.
 * - Space: O(P) for the P pruned tokens.
 */
export function tokensToPrune(tokens: string[], outcomes: SendOutcome[]): string[] {
  const dead: string[] = [];
  outcomes.forEach((o, i) => {
    if (o.success || !o.error || i >= tokens.length) return;
    const code = o.error.code;
    if (code === "messaging/registration-token-not-registered") dead.push(tokens[i]);
    else if (code === "messaging/invalid-argument" && /registration token/i.test(o.error.message ?? "")) dead.push(tokens[i]);
  });
  return dead;
}

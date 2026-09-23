import { test } from "node:test";
import assert from "node:assert/strict";
import { IMMEDIATE_LIMIT, WINDOW_MS, needsDigest, planNotification, tokensToPrune, type BurstWindow, type EventFacts } from "./burst";

const ev = (n: number): EventFacts => ({ type: "stop_moved", stopId: `s${n}`, stopName: `Stop ${n}`, day: 0 });

test("first event opens a window and is pushed at once", () => {
  const d = planNotification(undefined, 1_000, ev(1));
  assert.equal(d.action, "send_single");
  assert.equal(d.scheduleFlush, false);
  assert.deepEqual(d.window, { openedAt: 1_000, expiresAt: 1_000 + WINDOW_MS, count: 1, flushScheduled: false, latest: ev(1) });
});

test("three rapid edits: two singles, then a deferred digest scheduled once", () => {
  let w: BurstWindow | undefined;
  const actions: string[] = [];
  const flushes: boolean[] = [];
  for (let i = 1; i <= 5; i++) {
    const d = planNotification(w, 1_000 + i, ev(i));
    actions.push(d.action);
    flushes.push(d.scheduleFlush);
    w = d.scheduleFlush ? { ...d.window, flushScheduled: true } : d.window;
  }
  assert.deepEqual(actions, ["send_single", "send_single", "defer", "defer", "defer"]);
  assert.deepEqual(flushes, [false, false, true, false, false]);
  assert.equal(w!.count, 5);
  assert.equal(w!.latest.stopId, "s5");
  assert.equal(needsDigest(w!), true);
  assert.equal(IMMEDIATE_LIMIT, 2);
});

test("a failed enqueue is retried on the next deferred event", () => {
  const w: BurstWindow = { openedAt: 0, expiresAt: WINDOW_MS, count: 3, flushScheduled: false, latest: ev(3) };
  const d = planNotification(w, 10, ev(4));
  assert.equal(d.action, "defer");
  assert.equal(d.scheduleFlush, true);
});

test("an expired window starts over; an unflushed digest-sized one is flushed inline", () => {
  const stale: BurstWindow = { openedAt: 0, expiresAt: WINDOW_MS, count: 4, flushScheduled: false, latest: ev(4) };
  const d = planNotification(stale, WINDOW_MS + 1, ev(5));
  assert.equal(d.action, "send_single");
  assert.equal(d.window.count, 1);
  assert.deepEqual(d.flushStale, stale);
  const flushed: BurstWindow = { ...stale, flushScheduled: true };
  assert.equal(planNotification(flushed, WINDOW_MS + 1, ev(5)).flushStale, undefined);
  const small: BurstWindow = { ...stale, count: 2 };
  assert.equal(planNotification(small, WINDOW_MS + 1, ev(5)).flushStale, undefined);
  assert.equal(needsDigest(small), false);
});

test("prunes unregistered tokens but not a malformed payload", () => {
  const tokens = ["a", "b", "c", "d"];
  const pruned = tokensToPrune(tokens, [
    { success: true },
    { success: false, error: { code: "messaging/registration-token-not-registered" } },
    { success: false, error: { code: "messaging/invalid-argument", message: "The registration token is not a valid FCM registration token" } },
    { success: false, error: { code: "messaging/invalid-argument", message: "data must only contain string values" } },
  ]);
  assert.deepEqual(pruned, ["b", "c"]);
  assert.deepEqual(tokensToPrune(tokens, [{ success: false, error: { code: "messaging/internal-error" } }]), []);
});

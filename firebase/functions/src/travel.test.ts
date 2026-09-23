import { test } from "node:test";
import assert from "node:assert/strict";
import { CACHE_MS, adjacentPairs, legId, legsFromMatrix, matrixRequest, planLegs, routesErrorMessage, type LegDoc, type StopPoint } from "./travel";

const s = (id: string, i: number): StopPoint => ({ id, lat: 38.7 + i / 100, lng: -9.1 });
const A = s("a", 0), B = s("b", 1), C = s("c", 2), D = s("d", 3);

test("adjacent pairs of an ordered day", () => {
  assert.deepEqual(adjacentPairs([A, B, C]).map((p) => p.from.id + p.to.id), ["ab", "bc"]);
  assert.deepEqual(adjacentPairs([A]), []);
});

test("moving one stop recomputes only new adjacencies and drops the old one", () => {
  // Day was a -> b -> c -> d; c moved to the end: a -> b -> d -> c.
  const now = 1_000_000;
  const existing = new Map<string, LegDoc>();
  for (const [f, t] of [["a", "b"], ["b", "c"], ["c", "d"]] as const) {
    for (const mode of ["DRIVE", "WALK"] as const) {
      existing.set(legId(f, t, mode), { fromEventId: f, toEventId: t, mode, seconds: 60, meters: 500, computedAt: now - 1000 });
    }
  }
  const plan = planLegs(adjacentPairs([A, B, D, C]), new Set(["c"]), existing, now);
  assert.deepEqual(plan.toDelete.sort(), ["b_c_DRIVE", "b_c_WALK", "c_d_DRIVE", "c_d_WALK"]);
  assert.deepEqual(plan.toCompute.DRIVE.map((p) => p.from.id + p.to.id), ["bd", "dc"]);
  assert.deepEqual(plan.toCompute.WALK.map((p) => p.from.id + p.to.id), ["bd", "dc"]);
  // a -> b is a cache hit and untouched.
  assert.equal(plan.toCompute.DRIVE.some((p) => p.from.id === "a"), false);
});

test("stale and failed legs are recomputed; other days' legs are left alone", () => {
  const now = 5_000_000;
  const existing = new Map<string, LegDoc>([
    ["a_b_DRIVE", { fromEventId: "a", toEventId: "b", mode: "DRIVE", seconds: 1, meters: 1, computedAt: now - CACHE_MS - 1 }],
    ["a_b_WALK", { fromEventId: "a", toEventId: "b", mode: "WALK", error: "boom", computedAt: now }],
    ["x_y_DRIVE", { fromEventId: "x", toEventId: "y", mode: "DRIVE", seconds: 1, meters: 1, computedAt: now }],
  ]);
  const plan = planLegs(adjacentPairs([A, B]), new Set(["b"]), existing, now);
  assert.equal(plan.toCompute.DRIVE.length, 1);
  assert.equal(plan.toCompute.WALK.length, 1);
  assert.deepEqual(plan.toDelete, []);
});

test("matrix diagonal becomes legs; failures become error legs", () => {
  const pairs = adjacentPairs([A, B, C]);
  const legs = legsFromMatrix(pairs, "DRIVE", [
    { originIndex: 0, destinationIndex: 0, condition: "ROUTE_EXISTS", duration: "754s", distanceMeters: 4210.4 },
    { originIndex: 0, destinationIndex: 1, condition: "ROUTE_EXISTS", duration: "9s", distanceMeters: 1 },
    { originIndex: 1, destinationIndex: 1, condition: "ROUTE_NOT_FOUND" },
  ], 42);
  assert.deepEqual(legs[0], { fromEventId: "a", toEventId: "b", mode: "DRIVE", computedAt: 42, seconds: 754, meters: 4210 });
  assert.equal(legs[1].error, "ROUTE_NOT_FOUND");
  const req = matrixRequest(pairs, "DRIVE");
  assert.equal((req.origins as unknown[]).length, 2);
  assert.equal(req.routingPreference, "TRAFFIC_UNAWARE");
  assert.equal("routingPreference" in matrixRequest(pairs, "WALK"), false);
});

test("routes error bodies in array and object form", () => {
  assert.equal(routesErrorMessage(400, '[{"error":{"code":400,"message":"API key not valid."}}]'), "API key not valid.");
  assert.equal(routesErrorMessage(403, '{"error":{"message":"Routes API has not been used"}}'), "Routes API has not been used");
  assert.equal(routesErrorMessage(502, "<html>"), "Routes HTTP 502");
});

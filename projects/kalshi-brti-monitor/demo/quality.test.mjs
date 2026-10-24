import { test } from "node:test";
import assert from "node:assert/strict";
import { evaluateSnapshot } from "./quality.mjs";
const baseline = {
  state: "OPEN",
  ageMs: 1000,
  secondsRemaining: 120,
  pollMs: 700,
  feedsAligned: true,
};
test("fresh, aligned snapshot passes the sample checks", () =>
  assert.equal(evaluateSnapshot(baseline).ready, true));
for (const [label, patch] of Object.entries({
  stale: { ageMs: 5001 },
  expired: { secondsRemaining: 0 },
  finalSeconds: { secondsRemaining: 15 },
  slow: { pollMs: 2501 },
  inconsistent: { feedsAligned: false },
  closed: { state: "CLOSED" },
  missingAge: { ageMs: null },
  missingDuration: { pollMs: undefined },
})) {
  test(`${label} snapshot is held for review`, () =>
    assert.equal(evaluateSnapshot({ ...baseline, ...patch }).ready, false));
}
test("inclusive age and duration boundaries pass", () =>
  assert.equal(evaluateSnapshot({ ...baseline, ageMs: 5000, pollMs: 2500 }).ready, true));

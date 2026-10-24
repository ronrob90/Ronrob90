// A sample-data illustration of marketSnapshot() checks. No API access or trading.
export function evaluateSnapshot(sample) {
  const checks = [
    { label: "Contract is open", pass: sample.state === "OPEN" },
    {
      label: "Quote age is at most 5 seconds",
      pass: Number.isFinite(sample.ageMs) && sample.ageMs >= 0 && sample.ageMs <= 5000,
    },
    {
      label: "More than 15 seconds remain",
      pass: Number.isFinite(sample.secondsRemaining) && sample.secondsRemaining > 15,
    },
    {
      label: "Poll completes within 2.5 seconds",
      pass: Number.isFinite(sample.pollMs) && sample.pollMs >= 0 && sample.pollMs <= 2500,
    },
    { label: "Quote feeds agree", pass: sample.feedsAligned === true },
  ];
  return { checks, ready: checks.every((check) => check.pass) };
}

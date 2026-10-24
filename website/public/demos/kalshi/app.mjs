import { evaluateSnapshot } from './quality.mjs';
const base = { state: 'OPEN', ageMs: 1000, secondsRemaining: 120, pollMs: 700, feedsAligned: true };
const scenarios = {
  aligned: { ...base, explanation: 'Recent data, a quick response, and consistent feeds pass these sample checks.' },
  stale: { ...base, ageMs: 18000, explanation: 'An 18-second-old quote cannot represent a reliably current snapshot.' },
  mismatch: { ...base, feedsAligned: false, explanation: 'The feeds disagree. Fresh timestamps alone cannot resolve the inconsistency.' },
  closing: { ...base, secondsRemaining: 10, explanation: 'The remaining contract window is too short for this analysis threshold.' },
  slow: { ...base, pollMs: 3200, explanation: 'A slow poll can combine observations from different moments.' },
};
function render() {
  const sample = scenarios[document.querySelector('#scenario').value];
  const { checks, ready } = evaluateSnapshot(sample);
  document.querySelector('#explanation').textContent = sample.explanation;
  const metrics = document.querySelector('#metrics');
  metrics.replaceChildren();
  for (const [label, value] of [['Quote age', `${sample.ageMs / 1000} s`], ['Time to close', `${sample.secondsRemaining} s`], ['Poll duration', `${sample.pollMs / 1000} s`], ['Feed agreement', sample.feedsAligned ? 'Aligned' : 'Mismatch']]) {
    const term = document.createElement('dt'); term.textContent = label;
    const detail = document.createElement('dd'); detail.textContent = value;
    metrics.append(term, detail);
  }
  const outcome = document.querySelector('#outcome');
  outcome.textContent = ready ? 'Sample checks pass' : 'Hold for review';
  outcome.dataset.ready = String(ready);
  const list = document.querySelector('#checks'); list.replaceChildren();
  for (const check of checks) {
    const item = document.createElement('li');
    const badge = document.createElement('span'); badge.className = check.pass ? 'pass' : 'fail';
    badge.textContent = check.pass ? 'PASS' : 'HOLD';
    item.append(badge, document.createTextNode(check.label)); list.append(item);
  }
}
document.querySelector('#scenario').addEventListener('change', render);
render();

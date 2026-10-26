# Kalshi BRTI Market Monitor

A read-only Java/Spring Boot research application combining Bitcoin index readings, active contract details, and order-book snapshots. It checks whether a snapshot is current and consistent before presenting it for analysis.

## Problem and implementation

A quote, market record, and index value can describe different moments. This project tracks timestamps, finds the active 15-minute market, and exposes missing, stale, or ambiguous inputs.

The current source includes:

- Authenticated BRTI WebSocket integration, reconnect handling, and timestamp-aware index tracking.
- REST market discovery with pagination and a single active-window match requirement.
- Fresh market/order-book requests, normalized quotes, and agreement checks.
- Quote-age, poll-duration, and time-to-close checks.
- Local dashboards, JSON snapshots, a read-only MCP endpoint, technical indicators, and a local research recorder.
- Automated checks for market discovery, pagination, quote reconciliation, freshness boundaries, HTTP parsing, JSON compatibility, technical calculations, and index tracking.

The code is organized around a typed market-data pipeline. See the [architecture guide](docs/architecture.md) for the key files and the order of validation checks.

## Sample-data demo

The [`demo/`](demo) directory illustrates selected quote-quality checks with fictional scenarios. It requires no credentials and does not contact Kalshi. It is also packaged in the portfolio under `/demos/kalshi/`.

```sh
node --test demo/quality.test.mjs
python -m http.server 8000 --directory demo
```

Open `http://localhost:8000`. The back-to-portfolio link works when served inside the portfolio. This simplified demo is smaller than the Java application; its tests exercise stale, closed, inconsistent, slow, missing-data, and timing-boundary cases.

## Run the Java application

Requires Java 21 and internet access for the first Maven dependency download. The wrapper is included.

1. Open this folder as a Maven project.
2. Set `KALSHI_KEY_ID` and `KALSHI_PRIVATE_KEY_PATH` in the local run configuration. Store the key file outside the repository.
3. Optionally set `KALSHI_ENV` to `production` or `demo` to match the WebSocket credentials. The REST market service currently uses the production endpoint, so demo mode is not a complete end-to-end sandbox.
4. Run `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`).

Open `http://127.0.0.1:8080`. The application binds to localhost. Without credentials, the BRTI feed reports configuration required. Feed access depends on account permissions and availability.

`KALSHI_TARGET` is an optional initial manual target. A successfully discovered market supplies its own strike.

| Local path                    | Purpose                      |
| ----------------------------- | ---------------------------- |
| `/`                           | BRTI dashboard               |
| `/markets.html`               | Market dashboard             |
| `/intelligence.html`          | Technical-analysis dashboard |
| `/api/snapshot`               | BRTI snapshot                |
| `/api/market/current`         | Current market snapshot      |
| `/api/market/combined`        | Combined readiness           |
| `/api/intelligence/technical` | Technical indicators         |
| `/mcp`                        | Read-only JSON-RPC interface |

## Rapid BRTI reversal diagnostics (feature branch)

The `/api/snapshot` endpoint now includes a nested `reversal` object from
`BrtiReversalMath`. The established response fields are unchanged. The
diagnostic uses only the timestamped official BRTI values already received by
the WebSocket; it does **not** substitute Kraken/Webull spot prices.

- `change5s`, `change15s`, `change30s`, `change60s`: signed BRTI changes
  between the most recent observation and a source-timestamp-aligned anchor.
- `previous15sChange` and `acceleration15s`: the prior 15-second move and
  the difference between consecutive 15-second moves.
- `trailing60sAverageChange15s/30s/60s`: changes in the upstream-reported
  trailing average; these are not official settlement outcomes.
- `high60s`, `low60s`, `range60s`: observed values in the most recent
  minute when sufficient history exists.
- `strikeCrossingLast5s`: whether the latest BRTI crossed the current
  price-to-beat in the last five seconds.
- `alert`: `BULLISH_REVERSAL_WATCH`, `BEARISH_REVERSAL_WATCH`,
  `RAPID_UPWARD_MOVE`, `RAPID_DOWNWARD_MOVE`,
  `NO_HIGH_SPEED_ALERT`, or `UNAVAILABLE`.

Data ages and missing observations are explicit. A historical anchor more than
three seconds from its requested timestamp is unavailable, rather than
fabricated. History is bounded to 120 seconds and at most 150 samples.
Until enough samples accumulate, `status` is `PARTIAL_HISTORY`;
a stale feed returns `STALE` and `UNAVAILABLE` rather than a signal.

**Do not use these heuristic thresholds as a probability forecast or order
trigger.** They require historical replay/backtests, validation against
official Kalshi settlements, and accounting for spread/fees before evaluating
a trading edge. An alert may appear after a reversal has started, can miss
fast excursions between index updates, and does not imply a profitable fill.
The existing `NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED` policy is unchanged.

No trades are placed and no external exchange order-flow history is claimed.

## Verification

Run `./mvnw test` and `node --test demo/quality.test.mjs`. Tests need no live credentials; they do not establish live connectivity or forecast performance.

## Current limits

The application does not place orders. The combined snapshot deliberately retains `NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED`; estimated win probability and expected value are unset. Independently collected feeds are not an atomic market snapshot. Fresh data does not establish an executable fill or a forecasting edge.

Research output is local and excluded from Git. The localhost API is not an authenticated public multi-user service. Further work includes recorded-feed replay fixtures and evaluation of any future probability model against held-out data. Existing tests use fictional fixtures and a loopback HTTP server; they do not verify live exchange behavior.

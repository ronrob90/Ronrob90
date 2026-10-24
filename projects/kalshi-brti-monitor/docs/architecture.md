# Kalshi monitor architecture

The application polls public market data, receives BRTI index readings, and presents independently timestamped snapshots. It is a read-only research tool. Readiness flags describe data quality; they never authorize an order or claim a calibrated forecasting edge.

## Follow one market poll

`feed/KalshiMarketService.refresh()` is the entry point. It coordinates the following steps and publishes a complete immutable response only after validation:

1. `market/MarketDiscovery` finds exactly one contract in the current 15-minute window. It follows all bounded discovery pages before accepting a match. Future tradable contracts are excluded by their close time.
2. `market/MarketSnapshotFactory.validateSelected()` checks the ticker and positive strike. Discovery then caches only the validated ticker and close time, never a price or order book.
3. `market/KalshiMarketClient` fetches the order book and then a new reference market record. The client owns HTTP status handling, JSON parsing, and malformed-level filtering. All requests are GETs.
4. `market/MarketSnapshotFactory.create()` checks the reference ticker, active window, and strike. It derives YES asks from NO bids and NO asks from YES bids, preserving the opposite side's size. `feed/QuoteDecisionMath` reconciles the book against reference asks.
5. The service updates the BRTI comparison strike and publishes the market snapshot. Expected validation failures produce named unavailable states; transport failures produce sanitized errors and clear the previous strike.

`MarketDataClient` is the replaceable transport interface. Tests supply a fake implementation to cover pagination, rollover, failures, and scheduling-independent behavior. Production uses `KalshiMarketClient`.

## Types and JSON compatibility

`MarketContract`, `OrderBook`, `MarketSnapshot`, and `QuoteDecisionMath.Assessment` name the important values explicitly. `MarketSnapshot.Open` groups contract details, quotes, reference data, and freshness in Java. Jackson's `@JsonUnwrapped` preserves their existing flat JSON representation for the local dashboards and MCP clients.

`MarketSnapshot.Unavailable` deliberately has a smaller response: state, error, series, null price/asks, and freshness fields. The serialization tests pin both shapes, including nullable values and the combined intelligence response. The field fixture in `src/test/resources/contracts/market-open-keys.json` records the pre-refactor public contract.

BRTI and technical-indicator responses still use maps. They remain explicit compatibility boundaries in `CombinedMarketSnapshot` and `BtcIntelligenceController.IntelligenceSnapshot`; replacing every response type was outside this cleanup. New market-domain code should use named types.

## Time and readiness

A Spring `Clock` bean supplies UTC time to the polling service. `MarketSnapshot.at(now)` computes a fresh view without mutating the published snapshot, so data becomes stale even if the poller stops.

| Rule                      | Existing threshold                                                                                                        |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Current contract          | From `closeTime - 15 minutes`, inclusive, to `closeTime`, exclusive; status open/active and actual open time respected    |
| Market fresh              | Age below 15 seconds and contract not closed                                                                              |
| Quotes ready for analysis | Fresh; age at most 5 seconds; more than 15 whole seconds to close; poll duration at most 2.5 seconds; quote feeds aligned |
| Reference agreement       | Absolute ask difference at most $0.01 on both sides                                                                       |
| Trading decision          | Always `NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED`; probability and expected value remain null                           |

The quote-age clock starts when the REST poll completes. This measures local snapshot age, not guaranteed exchange tick age. Market, order-book, and BRTI requests remain asynchronous.

## Other entry points

- `feed/BrtiTracker`: index readings, published averages, freshness, and comparison strike.
- `feed/KalshiBrtiWebSocket`: authenticated feed connection and reconnect behavior.
- `feed/BtcTechnicalService`: candle retrieval and timeframe snapshots.
- `feed/BtcTechnicalMath`: deterministic EMA, RSI, ATR, ADX, and rolling VWAP calculations.
- `feed/KalshiResearchRecorder`: local sampled CSV output, with the existing column order preserved.
- `web/`: thin REST and MCP adapters. The MCP envelope remains a map because it carries several payload types.
- `src/main/resources/static/`: local dashboards for the Java application.
- `demo/`: independent fictional-data portfolio example, not a live market client.

## Regression coverage

| Test                      | Behavior protected                                                                                                       |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `MarketDiscoveryTest`     | Current-window boundaries, optional open time, pagination, ambiguity, cursor cycles, scan limit, cache refresh, rollover |
| `KalshiMarketClientTest`  | GET paths/query encoding, number parsing, sorted valid depth, malformed API shapes, sanitized HTTP errors                |
| `MarketSnapshotTest`      | Opposite-side ask/size derivation, freshness thresholds, reference validation, flat JSON contracts, no-trade fields      |
| `KalshiMarketServiceTest` | Request order, strike updates/clearing, failure redaction, cache invalidation, interrupt restoration, Spring wiring      |
| `QuoteDecisionMathTest`   | Agreement boundary, invalid/crossed books, size requirements, illustrative fee rounding                                  |
| `BtcTechnicalMathTest`    | Known EMA/RSI values, constant/directional series, stale/future candles, prior-bar ranges, volume-weighted VWAP          |
| `MarketApiContractTest`   | REST serialization and identical MCP text/structured payloads for all three tools                                        |
| `BrtiTrackerTest`         | Published final-minute averages and out-of-order readings                                                                |

These fixtures protect current arithmetic and data handling. Live exchange compatibility, credential permissions, actual fills, and forecasting performance require separate evaluation. The illustrative fee calculation is not a live fee lookup.

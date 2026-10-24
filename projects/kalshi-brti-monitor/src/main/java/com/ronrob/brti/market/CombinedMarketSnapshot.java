package com.ronrob.brti.market;

import java.math.BigDecimal;
import java.util.Map;

/** Market context and the existing BRTI response. Readiness is not a trading recommendation. */
public record CombinedMarketSnapshot(
    MarketSnapshot market,
    Map<String, Object> brti,
    boolean validForSettlementComparison,
    boolean quoteAndSettlementDataReadyForAnalysis,
    String tradeDecision,
    BigDecimal estimatedWinProbability,
    BigDecimal expectedValue,
    String warning
) {
    public static CombinedMarketSnapshot from(MarketSnapshot market, Map<String, Object> brti) {
        boolean ready =
            market.marketFresh() &&
            Boolean.TRUE.equals(brti.get("dataFresh")) &&
            market.priceToBeat() != null;
        return new CombinedMarketSnapshot(
            market,
            brti,
            ready,
            ready && market.quoteReadyForAnalysis(),
            "NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED",
            null,
            null,
            ready
                ? "Live index and market quote captured independently; timestamps differ, prices may change. Settlement requires official final-minute BRTI average."
                : "Missing or stale BRTI/market/strike; do NOT use this for trade decisions."
        );
    }
}

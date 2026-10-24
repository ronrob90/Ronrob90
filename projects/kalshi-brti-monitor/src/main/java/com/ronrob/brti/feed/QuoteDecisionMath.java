package com.ronrob.brti.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Pure, testable quote reconciliation and order-cost arithmetic.
 * This DOES NOT estimate the probability of a market settling YES.
 * These values are indicative, never guaranteed executable fills.
 */
public final class QuoteDecisionMath {
    private QuoteDecisionMath() {}
    public static final BigDecimal ONE = BigDecimal.ONE;
    public static final BigDecimal GENERAL_TAKER_RATE = new BigDecimal("0.07");
    // Safety threshold only: quote feeds may update independently. A >1 cent
    // discrepancy blocks evaluation; not a claim that the prices are incorrect.
    public static final BigDecimal MAX_REFERENCE_DIFFERENCE = new BigDecimal("0.0100");

    public static Map<String, Object> assess(BigDecimal yesBid, BigDecimal noBid,
             BigDecimal bookYesAsk, BigDecimal bookNoAsk,
             BigDecimal referenceYesAsk, BigDecimal referenceNoAsk,
             BigDecimal yesAskSize, BigDecimal noAskSize) {
        List<String> errors = new ArrayList<>();
        if (!validQuote(yesBid) || !validQuote(noBid) || !validQuote(bookYesAsk) || !validQuote(bookNoAsk)) {
            errors.add("EMPTY_OR_INVALID_ORDERBOOK");
        } else if (yesBid.add(noBid).compareTo(ONE) > 0 ||
                yesBid.compareTo(bookYesAsk) > 0 || noBid.compareTo(bookNoAsk) > 0) {
            errors.add("CROSSED_ORDERBOOK");
        }
        if (yesAskSize == null || noAskSize == null || yesAskSize.signum() <= 0 || noAskSize.signum() <= 0) {
            errors.add("MISSING_TOP_OF_BOOK_SIZE");
        }
        if (!validQuote(referenceYesAsk) || !validQuote(referenceNoAsk)) {
            errors.add("REFERENCE_QUOTE_UNAVAILABLE");
        }
        BigDecimal yesDifference = difference(bookYesAsk, referenceYesAsk);
        BigDecimal noDifference = difference(bookNoAsk, referenceNoAsk);
        if ((yesDifference != null && yesDifference.compareTo(MAX_REFERENCE_DIFFERENCE) > 0)
            || (noDifference != null && noDifference.compareTo(MAX_REFERENCE_DIFFERENCE) > 0)) {
            errors.add("QUOTE_FEEDS_DISAGREE");
        }
        boolean aligned = errors.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", aligned ? "ALIGNED" : "BLOCKED");
        result.put("canEvaluateQuotes", aligned);
        result.put("reasons", List.copyOf(errors));
        result.put("yesAskDifferenceDollars", yesDifference);
        result.put("noAskDifferenceDollars", noDifference);
        result.put("maxAllowedReferenceDifferenceDollars", MAX_REFERENCE_DIFFERENCE);
        result.put("assumption", "Public market quote and orderbook retrieved separately. Mismatch can reflect timing; do not infer edge or execute from inconsistent snapshots.");
        result.put("illustrativeOneContractFeesAndBreakevens", Map.of(
            "yes", feeSummary(bookYesAsk), "no", feeSummary(bookNoAsk)));
        return result;
    }

    static boolean validQuote(BigDecimal p) {
        return p != null && p.compareTo(BigDecimal.ZERO) > 0 && p.compareTo(ONE) < 0;
    }

    static BigDecimal difference(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.subtract(b).abs();
    }

    /**
     * 2026 Kalshi published GENERAL taker fee (multiplier 1) illustration.
     * Fee rounds up so total position cost is a multiple of a centicent ($0.0001).
     * Does not account for series overrides, slippage, other account fees, or selling fees.
     */
    public static BigDecimal illustrativeTakerFee(BigDecimal priceDollars, int contracts) {
        if (!validQuote(priceDollars) || contracts <= 0) return null;
        BigDecimal n = BigDecimal.valueOf(contracts);
        BigDecimal positionCost = priceDollars.multiply(n);
        BigDecimal rawFee = GENERAL_TAKER_RATE.multiply(n).multiply(priceDollars)
                .multiply(ONE.subtract(priceDollars));
        return positionCost.add(rawFee).setScale(4, RoundingMode.CEILING).subtract(positionCost);
    }

    public static Map<String, Object> feeSummary(BigDecimal priceDollars) {
        Map<String, Object> f = new LinkedHashMap<>();
        BigDecimal fee = illustrativeTakerFee(priceDollars, 1);
        f.put("indicativeAskDollars", priceDollars);
        f.put("generalTakerFeeDollarsOneContract", fee);
        f.put("breakevenYesOrNoProbabilityOneContract", fee == null ? null : priceDollars.add(fee));
        f.put("feeModel", "Illustrative Jul 2026 general taker M=1; 1 contract; rounded to $0.0001. Verify Kalshi order preview / current series fee rules. NO win-probability estimated.");
        return f;
    }
}

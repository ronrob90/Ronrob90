package com.ronrob.brti.feed;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only final-minute settlement arithmetic using Kalshi's upstream
 * last_60s_windowed_average_15min and window_size, NOT spot candles.
 * No win probability, fills, or official finalized outcome is inferred.
 */
public final class BrtiSettlementMath {
    private static final long QUARTER_HOUR_MS = 900_000L;
    private static final long FINAL_MINUTE_START_MS = 840_000L;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal HALF_CENT = new BigDecimal("0.005");

    private BrtiSettlementMath() {}

    public static Map<String, Object> analyze(
        BigDecimal strike,
        BigDecimal latestBrti,
        BigDecimal serverPartialAverage,
        Integer serverSampleCount,
        Long observedAtMs,
        boolean dataFresh
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", "Server-published final-minute BRTI running average, 60 equally weighted readings");
        out.put("roundingAssumption", "HALF_UP to two decimals; verify Kalshi's exact halfway-tie policy");
        out.put("officialOutcomeVerified", false);
        out.put("warning", "Arithmetic only. Partial upstream averages may be rounded. This is not a calibrated forecast or an official settlement confirmation.");
        if (!dataFresh || observedAtMs == null) {
            out.put("status", "STALE_OR_MISSING");
            return out;
        }
        if (strike == null || strike.signum() <= 0) {
            out.put("status", "NO_VALID_STRIKE");
            return out;
        }
        long offsetMs = Math.floorMod(observedAtMs, QUARTER_HOUR_MS);
        int secondsToEnd = (int) ((QUARTER_HOUR_MS - offsetMs + 999L) / 1_000L);
        out.put("secondsToWindowEnd", secondsToEnd);
        if (offsetMs < FINAL_MINUTE_START_MS) {
            out.put("status", "WAITING_FOR_FINAL_MINUTE");
            return out;
        }
        if (serverPartialAverage == null || serverSampleCount == null) {
            out.put("status", "NO_SERVER_FINAL_MINUTE_AVERAGE");
            return out;
        }
        if (serverSampleCount < 1 || serverSampleCount > 60 || serverPartialAverage.signum() <= 0) {
            out.put("status", "INVALID_SERVER_WINDOW");
            return out;
        }
        int remaining = 60 - serverSampleCount;
        out.put("observationsUsed", serverSampleCount);
        out.put("observationsRemaining", remaining);
        out.put("partialAverage", serverPartialAverage);
        out.put("partialRoundedToCentsAssumingHalfUp", serverPartialAverage.setScale(2, RoundingMode.HALF_UP));

        if (remaining == 0) {
            BigDecimal rounded = serverPartialAverage.setScale(2, RoundingMode.HALF_UP);
            out.put("status", "COMPLETE_UNVERIFIED");
            out.put("provisionalRoundedAverage", rounded);
            out.put("provisionalSide", rounded.compareTo(strike) >= 0 ? "YES" : "NO");
            return out;
        }

        BigDecimal collectedSumEstimate = serverPartialAverage.multiply(BigDecimal.valueOf(serverSampleCount));
        BigDecimal denom = BigDecimal.valueOf(remaining);
        BigDecimal requiredRaw = strike.multiply(SIXTY)
            .subtract(collectedSumEstimate)
            .divide(denom, MathContext.DECIMAL64);
        BigDecimal thresholdAssumingHalfUp = strike.subtract(HALF_CENT);
        BigDecimal requiredAssumingRounding = thresholdAssumingHalfUp.multiply(SIXTY)
            .subtract(collectedSumEstimate)
            .divide(denom, MathContext.DECIMAL64);
        out.put("status", "COLLECTING");
        out.put("last30Seconds", secondsToEnd <= 30);
        out.put("requiredRemainingAverageForYesIgnoringRounding", requiredRaw);
        out.put("requiredRemainingAverageForYesAssumingHalfUp", requiredAssumingRounding);

        if (latestBrti != null && latestBrti.signum() > 0) {
            // A flat future path is a sensitivity scenario, not a prediction.
            BigDecimal flatFinalMean = collectedSumEstimate
                .add(latestBrti.multiply(denom))
                .divide(SIXTY, MathContext.DECIMAL64);
            BigDecimal flatRounded = flatFinalMean.setScale(2, RoundingMode.HALF_UP);
            out.put("flatPriceScenarioFinalAverage", flatFinalMean);
            out.put("flatPriceScenarioRoundedAverage", flatRounded);
            out.put("flatPriceScenarioSide", flatRounded.compareTo(strike) >= 0 ? "YES" : "NO");
            out.put("extraAverageNeededAboveLatestBrti", requiredRaw.subtract(latestBrti));
        }
        return out;
    }
}

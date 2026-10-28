package com.ronrob.brti.feed;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, read-only index reversal diagnostics.
 *
 * Data comes only from timestamped official BRTI observations. No inferred order
 * flow, executable signal, settlement probability or automated trading.
 */
public final class BrtiReversalMath {

    private static final long ANCHOR_TOLERANCE_MS = 3_000L;
    private static final BigDecimal FIFTEEN_SECOND_SHARP_MOVE = new BigDecimal("20");
    private static final BigDecimal PREVIOUS_MOVE_OPPOSITE = new BigDecimal("15");
    private static final BigDecimal RAPID_FIFTEEN_SECOND_MOVE = new BigDecimal("40");
    private static final BigDecimal RAPID_FIVE_SECOND_MOVE = new BigDecimal("15");

    private BrtiReversalMath() {}

    public record Sample(long timestampMs, BigDecimal price, BigDecimal trailing60sAverage) {}

    public static Map<String, Object> analyze(
        List<Sample> observations,
        BigDecimal strike,
        boolean dataFresh
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataFresh", dataFresh);
        out.put("sampleCount", observations.size());
        out.put(
            "policy",
            "BRTI-only timing diagnostics. No validated win probabilities or trading instructions."
        );
        if (!dataFresh || observations.isEmpty()) {
            out.put("status", dataFresh ? "INSUFFICIENT_HISTORY" : "STALE");
            out.put("alert", "UNAVAILABLE");
            return out;
        }

        Sample latest = observations.get(observations.size() - 1);
        Sample at5 = atOrBefore(observations, latest.timestampMs() - 5_000);
        Sample at15 = atOrBefore(observations, latest.timestampMs() - 15_000);
        Sample at30 = atOrBefore(observations, latest.timestampMs() - 30_000);
        Sample at60 = atOrBefore(observations, latest.timestampMs() - 60_000);

        BigDecimal delta5 = change(latest.price(), price(at5));
        BigDecimal delta15 = change(latest.price(), price(at15));
        BigDecimal delta30 = change(latest.price(), price(at30));
        BigDecimal delta60 = change(latest.price(), price(at60));
        BigDecimal previous15 = change(price(at15), price(at30));
        BigDecimal acceleration15 = change(delta15, previous15);

        out.put("status", at60 == null ? "PARTIAL_HISTORY" : "READY");
        out.put("change5s", delta5);
        out.put("change15s", delta15);
        out.put("change30s", delta30);
        out.put("change60s", delta60);
        out.put("previous15sChange", previous15);
        out.put("acceleration15s", acceleration15);
        out.put(
            "trailing60sAverageChange15s",
            change(latest.trailing60sAverage(), at15 == null ? null : at15.trailing60sAverage())
        );
        out.put(
            "trailing60sAverageChange30s",
            change(latest.trailing60sAverage(), at30 == null ? null : at30.trailing60sAverage())
        );
        out.put(
            "trailing60sAverageChange60s",
            change(latest.trailing60sAverage(), at60 == null ? null : at60.trailing60sAverage())
        );

        if (at60 != null) {
            BigDecimal high = latest.price();
            BigDecimal low = latest.price();
            long floorMs = latest.timestampMs() - 60_000;
            for (Sample s : observations) {
                if (s.timestampMs() < floorMs) continue;
                if (s.price().compareTo(high) > 0) high = s.price();
                if (s.price().compareTo(low) < 0) low = s.price();
            }
            out.put("high60s", high);
            out.put("low60s", low);
            out.put("range60s", high.subtract(low));
            out.put("distanceFromHigh60s", latest.price().subtract(high));
            out.put("distanceFromLow60s", latest.price().subtract(low));
        } else {
            out.put("high60s", null);
            out.put("low60s", null);
            out.put("range60s", null);
            out.put("distanceFromHigh60s", null);
            out.put("distanceFromLow60s", null);
        }

        String crossing = "NONE";
        if (strike != null && at5 != null) {
            boolean wasAbove = at5.price().compareTo(strike) >= 0;
            boolean isAbove = latest.price().compareTo(strike) >= 0;
            if (!wasAbove && isAbove) crossing = "UP";
            if (wasAbove && !isAbove) crossing = "DOWN";
        }
        out.put("strikeCrossingLast5s", at5 == null ? "INSUFFICIENT_HISTORY" : crossing);

        String alert = "NO_HIGH_SPEED_ALERT";
        if (
            delta15 != null &&
            previous15 != null &&
            delta15.compareTo(FIFTEEN_SECOND_SHARP_MOVE) >= 0 &&
            previous15.compareTo(PREVIOUS_MOVE_OPPOSITE.negate()) <= 0
        ) {
            alert = "BULLISH_REVERSAL_WATCH";
        } else if (
            delta15 != null &&
            previous15 != null &&
            delta15.compareTo(FIFTEEN_SECOND_SHARP_MOVE.negate()) <= 0 &&
            previous15.compareTo(PREVIOUS_MOVE_OPPOSITE) >= 0
        ) {
            alert = "BEARISH_REVERSAL_WATCH";
        } else if (
            delta15 != null &&
            delta5 != null &&
            delta15.compareTo(RAPID_FIFTEEN_SECOND_MOVE) >= 0 &&
            delta5.compareTo(RAPID_FIVE_SECOND_MOVE) >= 0
        ) {
            alert = "RAPID_UPWARD_MOVE";
        } else if (
            delta15 != null &&
            delta5 != null &&
            delta15.compareTo(RAPID_FIFTEEN_SECOND_MOVE.negate()) <= 0 &&
            delta5.compareTo(RAPID_FIVE_SECOND_MOVE.negate()) <= 0
        ) {
            alert = "RAPID_DOWNWARD_MOVE";
        }
        out.put("alert", alert);
        out.put(
            "warning",
            "Price change thresholds are heuristic watch flags, not calibrated forecasts. Confirm against official final-minute BRTI average."
        );
        return out;
    }

    private static Sample atOrBefore(List<Sample> observations, long targetMs) {
        for (int i = observations.size() - 1; i >= 0; i--) {
            Sample item = observations.get(i);
            if (item.timestampMs() <= targetMs) {
                return targetMs - item.timestampMs() <= ANCHOR_TOLERANCE_MS ? item : null;
            }
        }
        return null;
    }

    private static BigDecimal price(Sample sample) {
        return sample == null ? null : sample.price();
    }

    private static BigDecimal change(BigDecimal newer, BigDecimal older) {
        return newer == null || older == null ? null : newer.subtract(older);
    }
}

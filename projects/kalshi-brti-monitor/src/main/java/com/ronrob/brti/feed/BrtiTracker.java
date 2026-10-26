package com.ronrob.brti.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Holds only BRTI market data; never stores trade orders or credentials. */
@Component
public class BrtiTracker {

    private final ObjectMapper mapper;
    private static final long REVERSAL_HISTORY_MS = 120_000L;
    private static final int MAX_REVERSAL_SAMPLES = 150;
    private final Deque<BrtiReversalMath.Sample> reversalSamples = new ArrayDeque<>();
    private BigDecimal strike;
    private BigDecimal latest;
    private Long sourceTimestampMs;
    private Long receivedLocallyMs;
    private BigDecimal rolling60;
    private Integer rollingCount;
    private BigDecimal finalMinuteAverage;
    private Integer finalMinuteCount;
    private String connectionStatus = "NOT_CONNECTED";
    private String lastError;

    public BrtiTracker(ObjectMapper mapper) {
        this.mapper = mapper;
        String initial = System.getenv("KALSHI_TARGET");
        if (initial != null && !initial.isBlank()) {
            setStrike(new BigDecimal(initial.trim()));
        }
    }

    public synchronized void setStrike(BigDecimal newStrike) {
        if (
            newStrike == null ||
            newStrike.compareTo(BigDecimal.ZERO) <= 0 ||
            newStrike.compareTo(new BigDecimal("10000000")) >= 0
        ) {
            throw new IllegalArgumentException("Enter a valid positive BTC price to beat.");
        }
        if (strike == null || strike.compareTo(newStrike) != 0) {
            // A new contract must not inherit the previous strike's reversal
            // window or settle using a tick observed before discovery.
            reversalSamples.clear();
            latest = null;
            rolling60 = null;
            rollingCount = null;
            finalMinuteAverage = null;
            finalMinuteCount = null;
            receivedLocallyMs = null;
        }
        strike = newStrike;
    }

    /** Clear old contract target when current-window discovery fails or rolls over. */
    public synchronized void clearStrike() {
        strike = null;
        reversalSamples.clear();
        finalMinuteAverage = null;
        finalMinuteCount = null;
    }

    public synchronized void status(String status, String error) {
        connectionStatus = status;
        lastError = error;
    }

    /** Parses the documented Kalshi cfbenchmarks_value message. */
    public synchronized void accept(JsonNode root) throws Exception {
        if (!"cfbenchmarks_value".equals(root.path("type").asText())) return;
        JsonNode msg = root.path("msg");
        if (!"BRTI".equals(msg.path("index_id").asText())) return;
        JsonNode upstream = mapper.readTree(msg.path("data").asText());
        if (!upstream.hasNonNull("value") || !upstream.hasNonNull("time")) return;
        long timestamp = upstream.path("time").asLong();
        if (timestamp <= 0) return;
        // Discard delayed, duplicate or out-of-order observations.
        if (sourceTimestampMs != null && timestamp <= sourceTimestampMs) return;
        latest = new BigDecimal(upstream.path("value").asText());
        sourceTimestampMs = timestamp;
        receivedLocallyMs = System.currentTimeMillis();
        JsonNode trailing = msg.path("avg_60s_data");
        rolling60 = moneyOrNull(trailing.path("value"));
        rollingCount = trailing.has("window_size") ? trailing.path("window_size").asInt() : null;
        // Bounded BRTI history for reversal watches. This uses source timestamps,
        // not the arrival time or an unrelated spot exchange.
        reversalSamples.addLast(new BrtiReversalMath.Sample(timestamp, latest, rolling60));
        long oldestAllowed = timestamp - REVERSAL_HISTORY_MS;
        while (!reversalSamples.isEmpty() &&
               (reversalSamples.peekFirst().timestampMs() < oldestAllowed ||
                reversalSamples.size() > MAX_REVERSAL_SAMPLES)) {
            reversalSamples.removeFirst();
        }
        JsonNode settlement = msg.path("last_60s_windowed_average_15min");
        // Present only during the minute before each :00/:15/:30/:45 close.
        finalMinuteAverage = moneyOrNull(settlement.path("value"));
        finalMinuteCount = settlement.has("window_size")
            ? settlement.path("window_size").asInt()
            : null;
        lastError = null;
    }

    private BigDecimal moneyOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() || node.asText().isBlank()
            ? null
            : new BigDecimal(node.asText());
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        Long ageMs = receivedLocallyMs == null ? null : now - receivedLocallyMs;
        boolean fresh =
            sourceTimestampMs != null &&
            ageMs != null &&
            ageMs <= 5000 &&
            Math.abs(now - sourceTimestampMs) <= 15000;
        result.put("status", connectionStatus);
        result.put("error", lastError);
        result.put("indexId", "BRTI");
        result.put("dataFresh", fresh);
        result.put("dataAgeMs", ageMs);
        result.put(
            "observedAt",
            sourceTimestampMs == null ? null : Instant.ofEpochMilli(sourceTimestampMs).toString()
        );
        result.put("brti", latest);
        result.put("strike", strike);
        result.put("gap", latest != null && strike != null ? latest.subtract(strike) : null);
        result.put("trailing60sAverage", rolling60);
        result.put("trailing60sCount", rollingCount);
        result.put("reversal", BrtiReversalMath.analyze(new ArrayList<>(reversalSamples), strike, fresh));
        result.put("finalMinuteAverage", finalMinuteAverage);
        result.put("finalMinuteCount", finalMinuteCount);
        BigDecimal required = null;
        // This is a conditional arithmetic illustration assuming 60 equally-weighted
        // index samples in the window. Kalshi's server-provided average is authoritative.
        if (
            strike != null &&
            finalMinuteAverage != null &&
            finalMinuteCount != null &&
            finalMinuteCount > 0 &&
            finalMinuteCount < 60
        ) {
            int left = 60 - finalMinuteCount;
            required = strike
                .multiply(BigDecimal.valueOf(60))
                .subtract(finalMinuteAverage.multiply(BigDecimal.valueOf(finalMinuteCount)))
                .divide(BigDecimal.valueOf(left), MathContext.DECIMAL64);
        }
        result.put("requiredRemainingAverageAssuming60Samples", required);
        result.put(
            "warning",
            !fresh
                ? "DATA NOT FRESH. Do not use for settlement decisions."
                : "Index feed is live; final settlement uses the official published 60-second BRTI average."
        );
        return result;
    }
}

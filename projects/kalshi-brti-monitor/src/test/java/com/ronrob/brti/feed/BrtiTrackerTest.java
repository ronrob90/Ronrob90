package com.ronrob.brti.feed;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrtiTrackerTest {

    @Test
    void usesServerPublishedFinalMinuteAverageAndShowsRemainingAverage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BrtiTracker tracker = new BrtiTracker(mapper);
        tracker.setStrike(new BigDecimal("81529.00"));
        long now = System.currentTimeMillis();
        String payload = """
        {"type":"cfbenchmarks_value","msg":{"index_id":"BRTI",
          "data":"{\\"type\\":\\"value\\",\\"id\\":\\"BRTI\\",\\"time\\":%d,\\"value\\":\\"81530.50\\"}",
          "avg_60s_data":{"value":"81528.50","window_size":30},
          "last_60s_windowed_average_15min":{"value":"81527.00","window_size":15}}}
        """.formatted(now);
        tracker.accept(mapper.readTree(payload));
        Map<String, Object> snapshot = tracker.snapshot();
        assertEquals(new BigDecimal("81530.50"), snapshot.get("brti"));
        assertEquals(new BigDecimal("81528.50"), snapshot.get("trailing60sAverage"));
        assertEquals(new BigDecimal("81527.00"), snapshot.get("finalMinuteAverage"));
        BigDecimal required = (BigDecimal) snapshot.get(
            "requiredRemainingAverageAssuming60Samples"
        );
        assertEquals(0, required.compareTo(new BigDecimal("81529.66666666667")));
        assertEquals(true, snapshot.get("dataFresh"));
    }

    @Test
    void ignoresAnOutOfOrderReading() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BrtiTracker tracker = new BrtiTracker(mapper);
        long timestamp = System.currentTimeMillis();
        tracker.accept(mapper.readTree(message(timestamp, "81001")));
        tracker.accept(mapper.readTree(message(timestamp - 1000, "99999")));
        assertEquals(new BigDecimal("81001"), tracker.snapshot().get("brti"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesTimeAlignedBearishReversalInLiveSnapshot() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BrtiTracker tracker = new BrtiTracker(mapper);
        tracker.setStrike(new BigDecimal("80030"));
        long startingTime = System.currentTimeMillis() - 60_000;
        for (int i = 0; i <= 60; i++) {
            int price = i <= 45 ? 80_000 + 2 * i : 80_090 - 5 * (i - 45);
            tracker.accept(mapper.readTree(message(startingTime + i * 1_000L, Integer.toString(price))));
        }
        Map<String, Object> reversal = (Map<String, Object>) tracker.snapshot().get("reversal");
        assertNotNull(reversal);
        assertEquals("READY", reversal.get("status"));
        assertEquals("BEARISH_REVERSAL_WATCH", reversal.get("alert"));
        assertEquals(new BigDecimal("-75"), reversal.get("change15s"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void newStrikeClearsOldReversalHistoryWithoutClearingOnSameStrike() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BrtiTracker tracker = new BrtiTracker(mapper);
        tracker.setStrike(new BigDecimal("80000"));
        long start = System.currentTimeMillis() - 10_000;
        for (int i = 0; i <= 10; i++) {
            tracker.accept(mapper.readTree(message(start + 1_000L * i, Integer.toString(80000 + i))));
        }
        tracker.setStrike(new BigDecimal("80000.00"));
        Map<String, Object> same = (Map<String, Object>) tracker.snapshot().get("reversal");
        assertEquals(11, same.get("sampleCount"));
        tracker.setStrike(new BigDecimal("80100"));
        Map<String, Object> changed = tracker.snapshot();
        Map<String, Object> reset = (Map<String, Object>) changed.get("reversal");
        assertEquals(0, reset.get("sampleCount"));
        assertEquals("STALE", reset.get("status"));
        assertEquals(false, changed.get("dataFresh"));
        assertNull(changed.get("gap"));
        tracker.accept(mapper.readTree(message(start + 11_000L, "80025")));
        Map<String, Object> newSnapshot = tracker.snapshot();
        Map<String, Object> newReversal = (Map<String, Object>) newSnapshot.get("reversal");
        assertEquals(1, newReversal.get("sampleCount"));
        assertEquals("PARTIAL_HISTORY", newReversal.get("status"));
        assertEquals(new BigDecimal("-75"), newSnapshot.get("gap"));
    }

    private String message(long timestamp, String price) {
        return """
        {"type":"cfbenchmarks_value","msg":{"index_id":"BRTI",
          "data":"{\\"time\\":%d,\\"value\\":\\"%s\\"}"}}
        """.formatted(timestamp, price);
    }
}

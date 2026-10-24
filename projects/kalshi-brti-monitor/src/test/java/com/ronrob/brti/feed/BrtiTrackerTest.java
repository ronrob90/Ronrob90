package com.ronrob.brti.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
        BigDecimal required = (BigDecimal) snapshot.get("requiredRemainingAverageAssuming60Samples");
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

    private String message(long timestamp, String price) {
        return """
                {"type":"cfbenchmarks_value","msg":{"index_id":"BRTI",
                  "data":"{\\"time\\":%d,\\"value\\":\\"%s\\"}"}}
                """.formatted(timestamp, price);
    }
}

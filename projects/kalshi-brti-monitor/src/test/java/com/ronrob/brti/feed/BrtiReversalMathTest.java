package com.ronrob.brti.feed;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrtiReversalMathTest {
    private static final long BASE = 1_782_000_000_000L;

    @Test
    void detectsSharpBearishTurnAfterPositiveMomentum() {
        List<BrtiReversalMath.Sample> samples = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            int price = i <= 45 ? 80_000 + 2 * i : 80_090 - 5 * (i - 45);
            samples.add(sample(i, price));
        }
        Map<String, Object> result = BrtiReversalMath.analyze(
            samples,
            new BigDecimal("80030"),
            true
        );
        assertEquals("READY", result.get("status"));
        assertEquals("BEARISH_REVERSAL_WATCH", result.get("alert"));
        assertEquals(new BigDecimal("-75"), result.get("change15s"));
        assertEquals(new BigDecimal("30"), result.get("previous15sChange"));
        assertEquals(new BigDecimal("-105"), result.get("acceleration15s"));
        assertEquals(new BigDecimal("90"), result.get("range60s"));
    }

    @Test
    void detectsFastBullishTurnAfterSelling() {
        List<BrtiReversalMath.Sample> samples = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            int price = i <= 45 ? 80_100 - 2 * i : 80_010 + 5 * (i - 45);
            samples.add(sample(i, price));
        }
        Map<String, Object> result = BrtiReversalMath.analyze(samples, null, true);
        assertEquals("BULLISH_REVERSAL_WATCH", result.get("alert"));
        assertEquals(new BigDecimal("75"), result.get("change15s"));
        assertEquals(new BigDecimal("-30"), result.get("previous15sChange"));
        assertEquals("NONE", result.get("strikeCrossingLast5s"));
    }

    @Test
    void missingHistoryDoesNotMakeUpChanges() {
        Map<String, Object> partial = BrtiReversalMath.analyze(
            List.of(sample(0, 80_000), sample(1, 80_035)),
            new BigDecimal("80010"),
            true
        );
        assertEquals("PARTIAL_HISTORY", partial.get("status"));
        assertNull(partial.get("change5s"));
        assertNull(partial.get("change60s"));
        assertEquals("NO_HIGH_SPEED_ALERT", partial.get("alert"));
        Map<String, Object> stale = BrtiReversalMath.analyze(
            List.of(sample(0, 80_000), sample(1, 80_035)),
            new BigDecimal("80010"),
            false
        );
        assertEquals("STALE", stale.get("status"));
        assertEquals("UNAVAILABLE", stale.get("alert"));
    }

    @Test
    void reportsStrikeCrossingWithinLastFiveSeconds() {
        List<BrtiReversalMath.Sample> samples = new ArrayList<>();
        for (int i = 0; i <= 60; i++) samples.add(sample(i, i < 58 ? 80_000 : 80_040));
        Map<String, Object> output = BrtiReversalMath.analyze(
            samples,
            new BigDecimal("80020"),
            true
        );
        assertEquals("UP", output.get("strikeCrossingLast5s"));
        assertEquals(new BigDecimal("40"), output.get("change5s"));
    }

    private static BrtiReversalMath.Sample sample(int seconds, int price) {
        return new BrtiReversalMath.Sample(
            BASE + seconds * 1_000L,
            BigDecimal.valueOf(price),
            BigDecimal.valueOf(price)
        );
    }
}

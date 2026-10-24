package com.ronrob.brti.feed;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BtcTechnicalMathTest {

    private static final long START = Instant.parse("2026-10-24T12:00:00Z").getEpochSecond();
    private static final Instant LAST_CLOSE = Instant.ofEpochSecond(START + 60 * 60);

    private List<BtcTechnicalMath.Candle> candles(int direction) {
        var candles = new ArrayList<BtcTechnicalMath.Candle>();
        for (int i = 0; i < 60; i++) {
            double price = 100 + direction * i;
            candles.add(
                new BtcTechnicalMath.Candle(
                    START + i * 60,
                    price,
                    price + 1,
                    price - 1,
                    price,
                    price,
                    1,
                    1
                )
            );
        }
        return candles;
    }

    private double value(Map<String, Object> result, String key) {
        return ((Number) result.get(key)).doubleValue();
    }

    @Test
    void seedsEmaWithASimpleAverageAndAppliesExponentialWeights() {
        assertEquals(4.0, BtcTechnicalMath.ema(new double[] { 1, 2, 3, 4, 5 }, 3), 1e-9);
        assertThrows(IllegalArgumentException.class, () ->
            BtcTechnicalMath.ema(new double[] { 1, 2 }, 3)
        );
    }

    @Test
    void handlesFlatUpwardAndDownwardRsi() {
        assertEquals(
            50,
            BtcTechnicalMath.rsi14(
                IntStream.range(0, 30)
                    .mapToDouble(i -> 100)
                    .toArray()
            ),
            1e-9
        );
        assertEquals(
            100,
            BtcTechnicalMath.rsi14(
                IntStream.range(0, 30)
                    .mapToDouble(i -> i)
                    .toArray()
            ),
            1e-9
        );
        assertEquals(
            0,
            BtcTechnicalMath.rsi14(
                IntStream.range(0, 30)
                    .mapToDouble(i -> 100 - i)
                    .toArray()
            ),
            1e-9
        );
    }

    @Test
    void matchesKnownFlatSeriesIndicators() {
        var result = BtcTechnicalMath.analyze(candles(0), 1, LAST_CLOSE);
        assertEquals("READY", result.get("status"));
        assertEquals(100, value(result, "ema50"), 1e-9);
        assertEquals(50, value(result, "rsi14"), 1e-9);
        assertEquals(2, value(result, "atr14"), 1e-9);
        assertEquals(0, value(result, "adx14"), 1e-9);
        assertEquals(0, value(result, "plusDi14"), 1e-9);
        assertEquals(0, value(result, "minusDi14"), 1e-9);
    }

    @Test
    void recognizesConsistentDirectionalSeries() {
        var rising = BtcTechnicalMath.analyze(candles(1), 1, LAST_CLOSE);
        var falling = BtcTechnicalMath.analyze(candles(-1), 1, LAST_CLOSE);
        assertEquals("UPTREND", rising.get("trendStructure"));
        assertEquals("POSITIVE", rising.get("momentumContext"));
        assertEquals("DOWNTREND", falling.get("trendStructure"));
        assertEquals("NEGATIVE", falling.get("momentumContext"));
    }

    @Test
    void checksHistoryAndFinalizedCandleFreshness() {
        assertThrows(IllegalArgumentException.class, () ->
            BtcTechnicalMath.analyze(candles(0), 0, LAST_CLOSE)
        );
        assertEquals(
            "INSUFFICIENT_HISTORY",
            BtcTechnicalMath.analyze(candles(0).subList(0, 54), 1, LAST_CLOSE).get("status")
        );
        assertEquals(
            "READY",
            BtcTechnicalMath.analyze(candles(0), 1, LAST_CLOSE.plusSeconds(150)).get("status")
        );
        assertEquals(
            "STALE",
            BtcTechnicalMath.analyze(candles(0), 1, LAST_CLOSE.plusSeconds(151)).get("status")
        );
        assertEquals(
            "STALE",
            BtcTechnicalMath.analyze(candles(0), 1, LAST_CLOSE.minusSeconds(6)).get("status")
        );
    }

    @Test
    void excludesTheLatestBarFromPriorHighLowAndWeightsVwapByVolume() {
        var series = candles(0);
        series.set(59, new BtcTechnicalMath.Candle(START + 59 * 60, 100, 150, 50, 100, 110, 19, 1));
        var result = BtcTechnicalMath.analyze(series, 1, LAST_CLOSE);
        assertEquals(101, value(result, "previous20High"), 1e-9);
        assertEquals(99, value(result, "previous20Low"), 1e-9);
        assertEquals(105, value(result, "rollingVwap20ClosedBars"), 1e-9);
    }
}

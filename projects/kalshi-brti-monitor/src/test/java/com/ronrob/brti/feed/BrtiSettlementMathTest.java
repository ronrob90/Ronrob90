package com.ronrob.brti.feed;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrtiSettlementMathTest {

    private static final BigDecimal TARGET = new BigDecimal("81406.46");

    private static long at(String timestamp) {
        return Instant.parse(timestamp).toEpochMilli();
    }

    private static Map<String, Object> read(
        String at, BigDecimal price, BigDecimal partial, Integer count
    ) {
        return BrtiSettlementMath.analyze(
            TARGET, price, partial, count, at(at), true
        );
    }

    @Test
    void noFinalMinuteArithmeticBeforeMinuteStarts() {
        Map<String, Object> result = read(
            "2026-10-28T10:58:59Z", new BigDecimal("81440"),
            new BigDecimal("81410"), 20
        );
        assertEquals("WAITING_FOR_FINAL_MINUTE", result.get("status"));
        assertFalse(result.containsKey("requiredRemainingAverageForYesAssumingHalfUp"));
    }

    @Test
    void firstReadingCalculatesRemainingAverage() {
        Map<String, Object> result = read(
            "2026-10-28T10:59:00Z", new BigDecimal("81400"),
            new BigDecimal("81400"), 1
        );
        assertEquals("COLLECTING", result.get("status"));
        assertEquals(59, result.get("observationsRemaining"));
        assertEquals(false, result.get("last30Seconds"));
        assertBigDecimal("81406.56949152542", result.get("requiredRemainingAverageForYesIgnoringRounding"));
    }

    @Test
    void fortyObservationsShowsRawAndRoundingAwareThresholds() {
        Map<String, Object> result = read(
            "2026-10-28T10:59:40Z", new BigDecimal("81420"),
            new BigDecimal("81400"), 40
        );
        assertEquals("COLLECTING", result.get("status"));
        assertEquals(20, result.get("observationsRemaining"));
        assertEquals(true, result.get("last30Seconds"));
        assertBigDecimal("81419.38", result.get("requiredRemainingAverageForYesIgnoringRounding"));
        assertBigDecimal("81419.365", result.get("requiredRemainingAverageForYesAssumingHalfUp"));
        assertEquals("YES", result.get("flatPriceScenarioSide"));
        assertEquals(false, result.get("officialOutcomeVerified"));
    }

    @Test
    void fiftyNineObservationsLeavesOnlyOne() {
        Map<String, Object> result = read(
            "2026-10-28T10:59:59Z", new BigDecimal("81400"),
            new BigDecimal("81406.40"), 59
        );
        assertEquals(1, result.get("observationsRemaining"));
        assertBigDecimal("81410.00", result.get("requiredRemainingAverageForYesIgnoringRounding"));
        assertBigDecimal("81409.70", result.get("requiredRemainingAverageForYesAssumingHalfUp"));
    }

    @Test
    void completedMinuteRoundsToNearestCentAndIncludesYesEquality() {
        Map<String, Object> yes = read(
            "2026-10-28T10:59:59Z", new BigDecimal("81406.455"),
            new BigDecimal("81406.455"), 60
        );
        assertEquals("COMPLETE_UNVERIFIED", yes.get("status"));
        assertEquals(new BigDecimal("81406.46"), yes.get("provisionalRoundedAverage"));
        assertEquals("YES", yes.get("provisionalSide"));
        assertFalse(yes.containsKey("requiredRemainingAverageForYesIgnoringRounding"));

        Map<String, Object> no = read(
            "2026-10-28T10:59:59Z", new BigDecimal("81406.454"),
            new BigDecimal("81406.454"), 60
        );
        assertEquals("NO", no.get("provisionalSide"));
    }

    @Test
    void missingOrStaleDataNeverProducesASettlementSide() {
        Map<String, Object> stale = BrtiSettlementMath.analyze(
            TARGET, new BigDecimal("81400"), new BigDecimal("81400"),
            60, at("2026-10-28T10:59:59Z"), false
        );
        assertEquals("STALE_OR_MISSING", stale.get("status"));
        assertFalse(stale.containsKey("provisionalSide"));

        Map<String, Object> missing = read(
            "2026-10-28T10:59:15Z", new BigDecimal("81410"), null, null
        );
        assertEquals("NO_SERVER_FINAL_MINUTE_AVERAGE", missing.get("status"));
    }

    @Test
    void invalidCountsFailClosedAndMissingStrikeFailsClosed() {
        for (int count : new int[]{-1, 0, 61}) {
            Map<String, Object> result = read(
                "2026-10-28T10:59:35Z", new BigDecimal("81410"),
                new BigDecimal("81410"), count
            );
            assertEquals("INVALID_SERVER_WINDOW", result.get("status"));
        }
        Map<String, Object> missingStrike = BrtiSettlementMath.analyze(
            null, new BigDecimal("81410"), new BigDecimal("81410"),
            40, at("2026-10-28T10:59:40Z"), true
        );
        assertEquals("NO_VALID_STRIKE", missingStrike.get("status"));
    }

    private static void assertBigDecimal(String expected, Object actual) {
        assertInstanceOf(BigDecimal.class, actual);
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }
}

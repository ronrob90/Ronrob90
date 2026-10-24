package com.ronrob.brti.market;

import static com.ronrob.brti.market.MarketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronrob.brti.web.BtcIntelligenceController.IntelligenceSnapshot;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarketSnapshotTest {

    private MarketSnapshot.Open snapshot(long pollMs) throws Exception {
        return MarketSnapshotFactory.create(
            current(),
            book(),
            current(),
            NOW.minusMillis(pollMs),
            NOW
        );
    }

    @Test
    void derivesAsksAndSizesFromOppositeBids() throws Exception {
        var snapshot = snapshot(100);
        assertEquals(decimal("0.60"), snapshot.quotes().yesAskDollars());
        assertEquals(decimal("7"), snapshot.quotes().yesAskSize());
        assertEquals(decimal("0.45"), snapshot.quotes().noAskDollars());
        assertEquals(decimal("12"), snapshot.quotes().noAskSize());
        assertTrue(snapshot.quoteReadyForAnalysis());
    }

    @Test
    void keepsAgeAndPollThresholdsInclusiveOnlyWhereIntended() throws Exception {
        var snapshot = snapshot(2500);
        assertTrue(snapshot.at(NOW.plusMillis(5000)).quoteReadyForAnalysis());
        assertFalse(snapshot.at(NOW.plusMillis(5001)).quoteReadyForAnalysis());
        assertTrue(snapshot.at(NOW.plusMillis(14999)).marketFresh());
        assertFalse(snapshot.at(NOW.plusMillis(15000)).marketFresh());
        assertFalse(snapshot(2501).quoteReadyForAnalysis());
        assertEquals(0L, snapshot.at(NOW.minusMillis(1)).freshness().dataAgeMs());
        assertEquals(0L, snapshot.freshness().dataAgeMs()); // Reading an aged copy does not mutate published data.
    }

    @Test
    void blocksAnalysisWithinFifteenSecondsOfClose() throws Exception {
        Instant close = Instant.parse(current().closeTime());
        var before = MarketSnapshotFactory.create(
            current(),
            book(),
            current(),
            close.minusSeconds(16),
            close.minusSeconds(16)
        );
        assertTrue(before.quoteReadyForAnalysis());
        assertFalse(before.at(close.minusSeconds(15)).quoteReadyForAnalysis());
        assertFalse(before.at(close).marketFresh());
        assertEquals(0L, before.at(close.plusSeconds(1)).freshness().secondsToClose());
    }

    @Test
    void rejectsInvalidTickerMissingStrikeAndReferenceChanges() {
        assertState(
            "INVALID_MARKET",
            contract("../invalid", "active", current().closeTime(), "80000"),
            current()
        );
        assertState(
            "STRIKE_UNAVAILABLE",
            contract(TICKER, "active", current().closeTime(), null),
            current()
        );
        assertState(
            "REFERENCE_MARKET_MISMATCH",
            current(),
            contract("KXBTC15M-OTHER", "active", current().closeTime(), "80000")
        );
        assertState(
            "MARKET_NOT_ACTIVE",
            current(),
            contract(TICKER, "closed", current().closeTime(), "80000")
        );
        assertState(
            "STRIKE_CHANGED",
            current(),
            contract(TICKER, "active", current().closeTime(), "80001")
        );
    }

    private void assertState(String state, MarketContract selected, MarketContract reference) {
        var error = assertThrows(MarketUnavailableException.class, () ->
            MarketSnapshotFactory.create(selected, book(), reference, NOW, NOW)
        );
        assertEquals(state, error.state());
    }

    @Test
    void serializesTheExistingFlatOpenAndUnavailableContracts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Set<String> expected;
        try (var input = getClass().getResourceAsStream("/contracts/market-open-keys.json")) {
            expected = mapper.readValue(input, new TypeReference<Set<String>>() {});
        }
        JsonNode open = mapper.valueToTree(snapshot(100));
        assertEquals(expected, keys(open));
        assertEquals("ALIGNED", open.path("quoteQuality").path("status").asText());
        assertEquals(Set.of("priceDollars", "contracts"), keys(open.path("yesBids").get(0)));
        assertTrue(open.get("error").isNull());
        JsonNode missing = mapper.valueToTree(new MarketSnapshot.Unavailable("ERROR", "Fixture"));
        assertEquals(
            Set.of(
                "state",
                "error",
                "seriesTicker",
                "priceToBeat",
                "yesAskDollars",
                "noAskDollars",
                "dataAgeMs",
                "marketFresh",
                "secondsToClose",
                "quoteReadyForAnalysis"
            ),
            keys(missing)
        );
        assertTrue(missing.get("priceToBeat").isNull());
        assertFalse(missing.get("marketFresh").asBoolean());
    }

    @Test
    void preservesCombinedAndIntelligenceFieldsAndTheNoTradeDecision() throws Exception {
        var combined = CombinedMarketSnapshot.from(snapshot(100), Map.of("dataFresh", true));
        assertTrue(combined.validForSettlementComparison());
        var intelligence = new IntelligenceSnapshot(
            combined,
            Map.of("technicalContextReady", true),
            true,
            "fixture"
        );
        JsonNode json = new ObjectMapper().valueToTree(intelligence);
        assertEquals(
            Set.of(
                "market",
                "brti",
                "validForSettlementComparison",
                "quoteAndSettlementDataReadyForAnalysis",
                "tradeDecision",
                "estimatedWinProbability",
                "expectedValue",
                "warning",
                "technical",
                "combinedContextReady",
                "technicalWarning"
            ),
            keys(json)
        );
        assertEquals(
            "NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED",
            json.get("tradeDecision").asText()
        );
        assertTrue(json.get("estimatedWinProbability").isNull());
        assertTrue(json.get("expectedValue").isNull());
        assertFalse(
            CombinedMarketSnapshot.from(
                snapshot(100),
                Map.of("dataFresh", false)
            ).validForSettlementComparison()
        );
    }

    private static Set<String> keys(JsonNode json) {
        Set<String> result = new HashSet<>();
        json.fieldNames().forEachRemaining(result::add);
        return result;
    }
}

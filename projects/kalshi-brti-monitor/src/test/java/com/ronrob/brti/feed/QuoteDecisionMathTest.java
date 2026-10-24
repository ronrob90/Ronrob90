package com.ronrob.brti.feed;

import static com.ronrob.brti.market.MarketFixtures.decimal;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QuoteDecisionMathTest {

    private QuoteDecisionMath.Assessment assess(
        String yesBid,
        String noBid,
        String yesAsk,
        String noAsk,
        String referenceYes,
        String referenceNo,
        String yesSize,
        String noSize
    ) {
        return QuoteDecisionMath.assess(
            decimal(yesBid),
            decimal(noBid),
            decimal(yesAsk),
            decimal(noAsk),
            decimal(referenceYes),
            decimal(referenceNo),
            decimal(yesSize),
            decimal(noSize)
        );
    }

    @Test
    void acceptsExactlyOneCentDifferenceButBlocksAnythingLarger() {
        var aligned = assess("0.55", "0.40", "0.60", "0.45", "0.61", "0.45", "7", "12");
        assertTrue(aligned.canEvaluateQuotes());
        assertEquals("ALIGNED", aligned.status());
        var blocked = assess("0.55", "0.40", "0.60", "0.45", "0.6101", "0.45", "7", "12");
        assertFalse(blocked.canEvaluateQuotes());
        assertTrue(blocked.reasons().contains("QUOTE_FEEDS_DISAGREE"));
    }

    @Test
    void distinguishesCrossedBooksFromMissingQuotes() {
        var crossed = assess("0.70", "0.40", "0.60", "0.30", "0.60", "0.30", "1", "1");
        assertTrue(crossed.reasons().contains("CROSSED_ORDERBOOK"));
        var missing = assess(null, "0.40", "0.60", null, null, null, "0", null);
        assertTrue(missing.reasons().contains("EMPTY_OR_INVALID_ORDERBOOK"));
        assertTrue(missing.reasons().contains("MISSING_TOP_OF_BOOK_SIZE"));
        assertTrue(missing.reasons().contains("REFERENCE_QUOTE_UNAVAILABLE"));
        assertFalse(missing.canEvaluateQuotes());
        assertThrows(UnsupportedOperationException.class, () -> missing.reasons().clear());
    }

    @Test
    void roundsIllustrativeFeesUpToTheExistingCenticentPolicy() {
        assertEquals(
            0,
            decimal("0.0175").compareTo(QuoteDecisionMath.illustrativeTakerFee(decimal("0.50"), 1))
        );
        assertEquals(
            0,
            decimal("0.0007").compareTo(QuoteDecisionMath.illustrativeTakerFee(decimal("0.01"), 1))
        );
        assertEquals(
            0,
            decimal("0.0465").compareTo(QuoteDecisionMath.illustrativeTakerFee(decimal("0.33"), 3))
        );
        assertEquals(
            0,
            decimal("0.5175").compareTo(
                QuoteDecisionMath.feeSummary(
                    decimal("0.50")
                ).breakevenYesOrNoProbabilityOneContract()
            )
        );
    }

    @Test
    void doesNotInventFeesForInvalidPricesOrQuantities() {
        assertNull(QuoteDecisionMath.illustrativeTakerFee(null, 1));
        assertNull(QuoteDecisionMath.illustrativeTakerFee(decimal("0"), 1));
        assertNull(QuoteDecisionMath.illustrativeTakerFee(decimal("1"), 1));
        assertNull(QuoteDecisionMath.illustrativeTakerFee(decimal("0.5"), 0));
        assertNull(QuoteDecisionMath.feeSummary(null).breakevenYesOrNoProbabilityOneContract());
    }
}

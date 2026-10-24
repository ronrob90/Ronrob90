package com.ronrob.brti.market;

import com.ronrob.brti.feed.QuoteDecisionMath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Pure validation and reconciliation, separate from transport and scheduling. */
public final class MarketSnapshotFactory {

    private MarketSnapshotFactory() {}

    public static void validateSelected(MarketContract market) throws MarketUnavailableException {
        if (
            !market.ticker().matches("KXBTC15M-[A-Za-z0-9-]{4,100}")
        ) throw new MarketUnavailableException(
            "INVALID_MARKET",
            "Active ticker did not match the expected BTC15M series"
        );
        if (
            market.priceToBeat() == null || market.priceToBeat().signum() <= 0
        ) throw new MarketUnavailableException(
            "STRIKE_UNAVAILABLE",
            "Kalshi has not yet published this window's floor_strike"
        );
    }

    public static MarketSnapshot.Open create(
        MarketContract selected,
        OrderBook book,
        MarketContract reference,
        Instant pollStarted,
        Instant fetchedAt
    ) throws MarketUnavailableException {
        validateSelected(selected);
        if (!selected.ticker().equals(reference.ticker())) throw new MarketUnavailableException(
            "REFERENCE_MARKET_MISMATCH",
            "Reference market response did not match the selected ticker"
        );
        if (!reference.isCurrentAt(fetchedAt)) throw new MarketUnavailableException(
            "MARKET_NOT_ACTIVE",
            "Selected market is no longer in the current 15-minute window"
        );
        if (
            reference.priceToBeat() == null ||
            reference.priceToBeat().compareTo(selected.priceToBeat()) != 0
        ) throw new MarketUnavailableException(
            "STRIKE_CHANGED",
            "Reference market price-to-beat differs from discovery response"
        );

        OrderBook.BookLevel yesBid = book.bestYesBid();
        OrderBook.BookLevel noBid = book.bestNoBid();
        BigDecimal yesAsk = complement(noBid);
        BigDecimal noAsk = complement(yesBid);
        QuoteDecisionMath.Assessment quality = QuoteDecisionMath.assess(
            price(yesBid),
            price(noBid),
            yesAsk,
            noAsk,
            reference.yesAskDollars(),
            reference.noAskDollars(),
            size(noBid),
            size(yesBid)
        );
        var contract = new MarketSnapshot.ContractDetails(
            selected.ticker(),
            selected.title(),
            selected.openTime(),
            selected.closeTime(),
            selected.priceToBeat(),
            selected.volumeFp(),
            selected.openInterestFp()
        );
        var quotes = new MarketSnapshot.BookQuotes(
            price(yesBid),
            size(yesBid),
            yesAsk,
            size(noBid),
            price(noBid),
            size(noBid),
            noAsk,
            size(yesBid),
            book.yesBids(),
            book.noBids()
        );
        var referenceQuotes = new MarketSnapshot.ReferenceQuotes(
            reference.yesAskDollars(),
            reference.noAskDollars(),
            reference.updatedTime(),
            fetchedAt.toString()
        );
        long durationMs = Duration.between(pollStarted, fetchedAt).toMillis();
        var freshness = MarketSnapshot.Freshness.calculate(
            fetchedAt,
            MarketContract.parseInstant(selected.closeTime()),
            durationMs,
            quality.canEvaluateQuotes(),
            fetchedAt
        );
        return new MarketSnapshot.Open(
            "OPEN",
            null,
            MarketContract.SERIES,
            contract,
            quotes,
            referenceQuotes,
            quality,
            quality.canEvaluateQuotes(),
            durationMs,
            fetchedAt.toString(),
            "Read-only REST (~5s polling), not guaranteed fills. Market and book requests are asynchronous; quote mismatches block trade assessment. Break-even uses an illustrative general taker fee, not an approved order preview. No calibrated win probability.",
            freshness
        );
    }

    private static BigDecimal price(OrderBook.BookLevel level) {
        return level == null ? null : level.priceDollars();
    }

    private static BigDecimal size(OrderBook.BookLevel level) {
        return level == null ? null : level.contracts();
    }

    private static BigDecimal complement(OrderBook.BookLevel level) {
        return level == null ? null : BigDecimal.ONE.subtract(level.priceDollars());
    }
}

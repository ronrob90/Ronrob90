package com.ronrob.brti.market;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ronrob.brti.feed.QuoteDecisionMath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Immutable responses; unwrapped groups preserve the dashboard's existing flat JSON fields. */
public sealed interface MarketSnapshot permits MarketSnapshot.Open, MarketSnapshot.Unavailable {
    String state();
    BigDecimal priceToBeat();
    boolean marketFresh();
    boolean quoteReadyForAnalysis();
    MarketSnapshot at(Instant now);

    record ContractDetails(
        String ticker,
        String title,
        String openTime,
        String closeTime,
        BigDecimal priceToBeat,
        BigDecimal volumeFp,
        BigDecimal openInterestFp
    ) {}

    record BookQuotes(
        BigDecimal yesBidDollars,
        BigDecimal yesBidSize,
        BigDecimal yesAskDollars,
        BigDecimal yesAskSize,
        BigDecimal noBidDollars,
        BigDecimal noBidSize,
        BigDecimal noAskDollars,
        BigDecimal noAskSize,
        List<OrderBook.BookLevel> yesBids,
        List<OrderBook.BookLevel> noBids
    ) {
        public BookQuotes {
            yesBids = List.copyOf(yesBids);
            noBids = List.copyOf(noBids);
        }
    }

    record ReferenceQuotes(
        BigDecimal marketYesAskDollars,
        BigDecimal marketNoAskDollars,
        String referenceUpdatedTime,
        String referenceFetchedAt
    ) {}

    record Freshness(
        Long dataAgeMs,
        boolean marketFresh,
        Long secondsToClose,
        boolean quoteReadyForAnalysis
    ) {
        private static final long MAX_MARKET_AGE_MS = 15000;
        private static final long MAX_ANALYSIS_AGE_MS = 5000;
        private static final long MAX_POLL_DURATION_MS = 2500;
        private static final long MIN_ANALYSIS_SECONDS_TO_CLOSE = 15;

        static Freshness calculate(
            Instant fetchedAt,
            Instant close,
            long pollDurationMs,
            boolean aligned,
            Instant now
        ) {
            long age = Math.max(0, Duration.between(fetchedAt, now).toMillis());
            boolean fresh = close != null && age < MAX_MARKET_AGE_MS && now.isBefore(close);
            Long seconds =
                close == null ? null : Math.max(0, Duration.between(now, close).toSeconds());
            boolean ready =
                fresh &&
                age <= MAX_ANALYSIS_AGE_MS &&
                seconds > MIN_ANALYSIS_SECONDS_TO_CLOSE &&
                pollDurationMs <= MAX_POLL_DURATION_MS &&
                aligned;
            return new Freshness(age, fresh, seconds, ready);
        }
    }

    record Open(
        String state,
        String error,
        String seriesTicker,
        @JsonUnwrapped ContractDetails contract,
        @JsonUnwrapped BookQuotes quotes,
        @JsonUnwrapped ReferenceQuotes reference,
        QuoteDecisionMath.Assessment quoteQuality,
        boolean quoteFeedsAligned,
        long pollDurationMs,
        String fetchedAt,
        String warning,
        @JsonUnwrapped Freshness freshness
    ) implements MarketSnapshot {
        @Override
        public BigDecimal priceToBeat() {
            return contract.priceToBeat();
        }

        @Override
        public boolean marketFresh() {
            return freshness.marketFresh();
        }

        @Override
        public boolean quoteReadyForAnalysis() {
            return freshness.quoteReadyForAnalysis();
        }

        @Override
        public Open at(Instant now) {
            Freshness updated = Freshness.calculate(
                Instant.parse(fetchedAt),
                MarketContract.parseInstant(contract.closeTime()),
                pollDurationMs,
                quoteFeedsAligned,
                now
            );
            return new Open(
                state,
                error,
                seriesTicker,
                contract,
                quotes,
                reference,
                quoteQuality,
                quoteFeedsAligned,
                pollDurationMs,
                fetchedAt,
                warning,
                updated
            );
        }
    }

    record Unavailable(
        String state,
        String error,
        String seriesTicker,
        BigDecimal priceToBeat,
        BigDecimal yesAskDollars,
        BigDecimal noAskDollars,
        @JsonUnwrapped Freshness freshness
    ) implements MarketSnapshot {
        public Unavailable(String state, String error) {
            this(
                state,
                error,
                MarketContract.SERIES,
                null,
                null,
                null,
                new Freshness(null, false, null, false)
            );
        }

        @Override
        public boolean marketFresh() {
            return false;
        }

        @Override
        public boolean quoteReadyForAnalysis() {
            return false;
        }

        @Override
        public Unavailable at(Instant now) {
            return this;
        }
    }
}

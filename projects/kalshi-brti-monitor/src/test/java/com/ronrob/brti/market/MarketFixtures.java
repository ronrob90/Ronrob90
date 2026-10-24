package com.ronrob.brti.market;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Fictional values shared by deterministic tests; never contacts an exchange. */
public final class MarketFixtures {

    private MarketFixtures() {}

    public static final Instant NOW = Instant.parse("2026-10-24T12:05:00Z");
    public static final String TICKER = "KXBTC15M-TEST-1200";

    public static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    public static MarketContract current() {
        return contract(TICKER, "active", "2026-10-24T12:15:00Z", "80000");
    }

    public static MarketContract contract(
        String ticker,
        String status,
        String close,
        String strike
    ) {
        return new MarketContract(
            ticker,
            "Fictional BTC window",
            status,
            "2026-10-24T11:00:00Z",
            close,
            decimal(strike),
            decimal("0.60"),
            decimal("0.45"),
            decimal("100"),
            decimal("50"),
            NOW.toString()
        );
    }

    public static OrderBook book() {
        return new OrderBook(
            List.of(new OrderBook.BookLevel(decimal("0.55"), decimal("12"))),
            List.of(new OrderBook.BookLevel(decimal("0.40"), decimal("7")))
        );
    }

    public static class FakeClient implements MarketDataClient {

        public List<MarketPage> pages = List.of(new MarketPage(List.of(current()), ""));
        public final List<String> cursors = new ArrayList<>();
        public final List<String> calls = new ArrayList<>();
        public MarketContract reference = current();
        public OrderBook book = MarketFixtures.book();
        public IOException failure;
        public boolean interrupt;
        public long minClose;
        public long maxClose;

        private void check() throws IOException, InterruptedException {
            if (interrupt) throw new InterruptedException("fixture interrupt");
            if (failure != null) throw failure;
        }

        @Override
        public MarketPage listMarkets(long minClose, long maxClose, String cursor)
            throws IOException, InterruptedException {
            check();
            this.minClose = minClose;
            this.maxClose = maxClose;
            calls.add("list");
            cursors.add(cursor);
            return pages.get(cursors.size() - 1);
        }

        @Override
        public MarketContract getMarket(String ticker) throws IOException, InterruptedException {
            check();
            calls.add("market:" + ticker);
            return reference;
        }

        @Override
        public OrderBook getOrderBook(String ticker) throws IOException, InterruptedException {
            check();
            calls.add("book:" + ticker);
            return book;
        }
    }
}

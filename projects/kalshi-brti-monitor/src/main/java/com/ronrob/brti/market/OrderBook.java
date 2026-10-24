package com.ronrob.brti.market;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Bid levels in descending price order. YES asks come from NO bids and vice versa. */
public record OrderBook(List<BookLevel> yesBids, List<BookLevel> noBids) {
    public OrderBook {
        yesBids = sorted(yesBids);
        noBids = sorted(noBids);
    }

    private static List<BookLevel> sorted(List<BookLevel> levels) {
        return levels
            .stream()
            .sorted(Comparator.comparing(BookLevel::priceDollars).reversed())
            .toList();
    }

    public BookLevel bestYesBid() {
        return yesBids.isEmpty() ? null : yesBids.get(0);
    }

    public BookLevel bestNoBid() {
        return noBids.isEmpty() ? null : noBids.get(0);
    }

    public record BookLevel(BigDecimal priceDollars, BigDecimal contracts) {}
}

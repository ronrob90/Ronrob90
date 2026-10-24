package com.ronrob.brti.market;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Selects exactly one current contract across all bounded pages. Never caches quotes. */
@Component
public class MarketDiscovery {

    private static final int MAX_DISCOVERY_PAGES = 10;
    private final MarketDataClient client;
    private volatile CachedContract cached;

    public MarketDiscovery(MarketDataClient client) {
        this.client = client;
    }

    public MarketContract findCurrent(Instant now)
        throws IOException, InterruptedException, MarketUnavailableException {
        CachedContract candidate = cached;
        if (
            candidate != null &&
            !now.isBefore(candidate.close().minus(MarketContract.WINDOW)) &&
            now.isBefore(candidate.close())
        ) {
            MarketContract market = client.getMarket(candidate.ticker());
            if (
                candidate.ticker().equals(market.ticker()) && market.isCurrentAt(now)
            ) return market;
            invalidate();
        }
        return discover(now);
    }

    /** Remember an identifier only after the caller has validated its ticker and strike. */
    public void remember(MarketContract market) {
        Instant close = MarketContract.parseInstant(market.closeTime());
        cached = close == null ? null : new CachedContract(market.ticker(), close);
    }

    public void invalidate() {
        cached = null;
    }

    private MarketContract discover(Instant now)
        throws IOException, InterruptedException, MarketUnavailableException {
        String cursor = null;
        Set<String> visitedCursors = new HashSet<>();
        List<MarketContract> matching = new ArrayList<>();
        int marketsScanned = 0;
        int pagesScanned = 0;
        boolean complete = false;
        long windowSeconds = MarketContract.WINDOW.getSeconds();
        long currentWindowStart =
            Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        // No status filter: a future tradable contract is not necessarily the current window.
        for (int page = 0; page < MAX_DISCOVERY_PAGES; page++) {
            MarketDataClient.MarketPage response = client.listMarkets(
                currentWindowStart,
                currentWindowStart + 2 * windowSeconds,
                cursor
            );
            pagesScanned++;
            for (MarketContract market : response.markets()) {
                marketsScanned++;
                if (market.isCurrentAt(now)) matching.add(market);
            }
            String next = response.cursor();
            if (next == null || next.isBlank()) {
                complete = true;
                break;
            }
            if (!visitedCursors.add(next)) throw new IOException(
                "Market pagination repeated a cursor"
            );
            cursor = next;
        }
        if (!complete) throw new MarketUnavailableException(
            "DISCOVERY_INCOMPLETE",
            "Stopped after " +
                pagesScanned +
                " pages (" +
                marketsScanned +
                " records); could not verify unique current market"
        );
        if (matching.size() != 1) throw new MarketUnavailableException(
            "NO_UNAMBIGUOUS_MARKET",
            "Found " +
                matching.size() +
                " matching " +
                MarketContract.SERIES +
                " 15-minute windows after scanning " +
                marketsScanned +
                " records across " +
                pagesScanned +
                " pages"
        );
        return matching.get(0);
    }

    private record CachedContract(String ticker, Instant close) {}
}

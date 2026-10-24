package com.ronrob.brti.feed;

import com.ronrob.brti.market.CombinedMarketSnapshot;
import com.ronrob.brti.market.MarketContract;
import com.ronrob.brti.market.MarketDataClient;
import com.ronrob.brti.market.MarketDiscovery;
import com.ronrob.brti.market.MarketSnapshot;
import com.ronrob.brti.market.MarketSnapshotFactory;
import com.ronrob.brti.market.MarketUnavailableException;
import com.ronrob.brti.market.OrderBook;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Coordinates each read-only poll and publishes a complete, immutable market snapshot. */
@Service
public class KalshiMarketService {

    private final MarketDataClient client;
    private final MarketDiscovery discovery;
    private final BrtiTracker brti;
    private final Clock clock;
    private volatile MarketSnapshot latest = new MarketSnapshot.Unavailable(
        "STARTING",
        "Waiting for first Kalshi market request"
    );

    public KalshiMarketService(
        MarketDataClient client,
        MarketDiscovery discovery,
        BrtiTracker brti,
        Clock clock
    ) {
        this.client = client;
        this.discovery = discovery;
        this.brti = brti;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 1200, fixedDelayString = "${kalshi.market.poll-ms:5000}")
    public void refresh() {
        try {
            Instant pollStarted = clock.instant();
            MarketContract selected = discovery.findCurrent(pollStarted);
            MarketSnapshotFactory.validateSelected(selected);
            discovery.remember(selected);
            OrderBook book = client.getOrderBook(selected.ticker());
            // Fetch the reference AFTER the book; discovery quotes may already be old.
            MarketContract reference = client.getMarket(selected.ticker());
            MarketSnapshot.Open snapshot = MarketSnapshotFactory.create(
                selected,
                book,
                reference,
                pollStarted,
                clock.instant()
            );
            brti.setStrike(snapshot.priceToBeat());
            latest = snapshot;
        } catch (MarketUnavailableException ex) {
            if (
                "MARKET_NOT_ACTIVE".equals(ex.state()) || "STRIKE_CHANGED".equals(ex.state())
            ) discovery.invalidate();
            clear(ex.state(), ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            clear("ERROR", "Market polling interrupted");
        } catch (Exception ex) {
            // Do not expose response bodies, headers, or stack traces to browser/MCP clients.
            clear("ERROR", "Public Kalshi market API request failed: " + safeError(ex));
        }
    }

    public MarketSnapshot marketSnapshot() {
        return latest.at(clock.instant());
    }

    public CombinedMarketSnapshot combinedSnapshot() {
        return CombinedMarketSnapshot.from(marketSnapshot(), brti.snapshot());
    }

    private synchronized void clear(String state, String error) {
        brti.clearStrike();
        latest = new MarketSnapshot.Unavailable(state, error);
    }

    private static String safeError(Exception ex) {
        if (ex instanceof java.net.http.HttpTimeoutException) return "timeout";
        if (
            ex.getMessage() != null && ex.getMessage().matches("HTTP [0-9]{3}")
        ) return ex.getMessage();
        return ex.getClass().getSimpleName();
    }
}

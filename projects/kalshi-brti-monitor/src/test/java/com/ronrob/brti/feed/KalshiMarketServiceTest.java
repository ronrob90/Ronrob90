package com.ronrob.brti.feed;

import static com.ronrob.brti.market.MarketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronrob.brti.market.*;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class KalshiMarketServiceTest {

    private final FakeClient client = new FakeClient();
    private final BrtiTracker tracker = new BrtiTracker(new ObjectMapper());
    private final MutableClock clock = new MutableClock();
    private final KalshiMarketService service = new KalshiMarketService(
        client,
        new MarketDiscovery(client),
        tracker,
        clock
    );

    @Test
    void fetchesReferenceAfterTheBookAndPublishesTheStrike() {
        service.refresh();
        assertEquals(List.of("list", "book:" + TICKER, "market:" + TICKER), client.calls);
        assertEquals("OPEN", service.marketSnapshot().state());
        assertEquals(decimal("80000"), tracker.snapshot().get("strike"));
        clock.now = NOW.plusSeconds(15);
        assertFalse(service.marketSnapshot().marketFresh());
    }

    @Test
    void clearsTheOldStrikeAndRedactsUnexpectedErrors() {
        service.refresh();
        client.failure = new IOException("sensitive diagnostic content");
        service.refresh();
        var unavailable = assertInstanceOf(
            MarketSnapshot.Unavailable.class,
            service.marketSnapshot()
        );
        assertEquals("ERROR", unavailable.state());
        assertEquals("Public Kalshi market API request failed: IOException", unavailable.error());
        assertNull(tracker.snapshot().get("strike"));
        assertFalse(service.combinedSnapshot().validForSettlementComparison());
    }

    @Test
    void invalidatesDiscoveryWhenTheReferenceStrikeChanges() {
        client.reference = contract(TICKER, "active", current().closeTime(), "80001");
        service.refresh();
        assertEquals("STRIKE_CHANGED", service.marketSnapshot().state());
        assertNull(tracker.snapshot().get("strike"));
        client.reference = current();
        client.cursors.clear();
        client.calls.clear();
        service.refresh();
        assertEquals("list", client.calls.get(0));
        assertEquals("OPEN", service.marketSnapshot().state());
    }

    @Test
    void restoresTheThreadInterruptFlag() {
        client.interrupt = true;
        try {
            service.refresh();
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals("ERROR", service.marketSnapshot().state());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void springCanWireTheNewBoundariesWithoutStartingPollers() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MarketDataClient.class, () -> client);
            context.registerBean(BrtiTracker.class, () -> tracker);
            context.registerBean(Clock.class, () -> clock);
            context.register(MarketDiscovery.class, KalshiMarketService.class);
            context.refresh();
            assertEquals(
                "STARTING",
                context.getBean(KalshiMarketService.class).marketSnapshot().state()
            );
            assertTrue(client.calls.isEmpty());
        }
    }

    private static class MutableClock extends Clock {

        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

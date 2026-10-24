package com.ronrob.brti.market;

import static com.ronrob.brti.market.MarketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDiscoveryTest {

    @Test
    void usesTheSettlementWindowInsteadOfTradingOpenTime() {
        MarketContract market = current();
        assertFalse(market.isCurrentAt(Instant.parse("2026-10-24T11:59:59Z")));
        assertTrue(market.isCurrentAt(Instant.parse("2026-10-24T12:00:00Z")));
        assertTrue(market.isCurrentAt(Instant.parse("2026-10-24T12:14:59Z")));
        assertFalse(market.isCurrentAt(Instant.parse("2026-10-24T12:15:00Z")));
        assertFalse(contract(TICKER, "closed", market.closeTime(), "80000").isCurrentAt(NOW));
        assertFalse(contract(TICKER, "active", "invalid", "80000").isCurrentAt(NOW));
    }

    @Test
    void permitsMissingOpenTimeButRespectsAFutureOpenTime() {
        MarketContract base = current();
        for (String open : Arrays.asList(null, "", "2026-10-24T12:06:00Z")) {
            var market = new MarketContract(
                base.ticker(),
                base.title(),
                base.status(),
                open,
                base.closeTime(),
                base.priceToBeat(),
                base.yesAskDollars(),
                base.noAskDollars(),
                null,
                null,
                null
            );
            assertEquals(open == null || open.isEmpty(), market.isCurrentAt(NOW));
        }
    }

    @Test
    void followsPagesAndIgnoresFutureTradableContracts() throws Exception {
        var client = new FakeClient();
        client.pages = List.of(
            new MarketDataClient.MarketPage(
                List.of(contract("KXBTC15M-FUTURE", "active", "2026-10-24T12:30:00Z", "80000")),
                "next"
            ),
            new MarketDataClient.MarketPage(List.of(current()), "")
        );
        assertEquals(TICKER, new MarketDiscovery(client).findCurrent(NOW).ticker());
        assertEquals(Arrays.asList(null, "next"), client.cursors);
        assertEquals(Instant.parse("2026-10-24T12:00:00Z").getEpochSecond(), client.minClose);
        assertEquals(Instant.parse("2026-10-24T12:30:00Z").getEpochSecond(), client.maxClose);
    }

    @Test
    void refusesZeroOrMultipleCurrentContracts() {
        for (List<MarketContract> contracts : List.of(
            List.<MarketContract>of(),
            List.of(current(), current())
        )) {
            var client = new FakeClient();
            client.pages = List.of(new MarketDataClient.MarketPage(contracts, ""));
            var error = assertThrows(MarketUnavailableException.class, () ->
                new MarketDiscovery(client).findCurrent(NOW)
            );
            assertEquals("NO_UNAMBIGUOUS_MARKET", error.state());
        }
    }

    @Test
    void doesNotAcceptAMatchBeforeCompletingPagination() {
        var client = new FakeClient();
        var pages = new ArrayList<MarketDataClient.MarketPage>();
        for (int i = 0; i < 10; i++) pages.add(
            new MarketDataClient.MarketPage(i == 0 ? List.of(current()) : List.of(), "page-" + i)
        );
        client.pages = pages;
        var error = assertThrows(MarketUnavailableException.class, () ->
            new MarketDiscovery(client).findCurrent(NOW)
        );
        assertEquals("DISCOVERY_INCOMPLETE", error.state());
        assertEquals(10, client.cursors.size());
    }

    @Test
    void detectsCursorCycles() {
        var client = new FakeClient();
        client.pages = List.of(
            new MarketDataClient.MarketPage(List.of(current()), "loop"),
            new MarketDataClient.MarketPage(List.of(), "loop")
        );
        assertThrows(IOException.class, () -> new MarketDiscovery(client).findCurrent(NOW));
    }

    @Test
    void cachesOnlyTheIdentifierAndDiscoversAgainAtRollover() throws Exception {
        var client = new FakeClient();
        var discovery = new MarketDiscovery(client);
        discovery.remember(current());
        assertEquals(current(), discovery.findCurrent(NOW));
        assertEquals(List.of("market:" + TICKER), client.calls);
        MarketContract next = contract("KXBTC15M-NEXT", "active", "2026-10-24T12:30:00Z", "80010");
        client.pages = List.of(new MarketDataClient.MarketPage(List.of(next), ""));
        assertEquals(next, discovery.findCurrent(Instant.parse("2026-10-24T12:15:00Z")));
        assertEquals(List.of("market:" + TICKER, "list"), client.calls);
    }

    @Test
    void rediscoversWhenTheCachedContractIsNoLongerActive() throws Exception {
        var client = new FakeClient();
        client.reference = contract(TICKER, "closed", current().closeTime(), "80000");
        var discovery = new MarketDiscovery(client);
        discovery.remember(current());
        assertEquals(current(), discovery.findCurrent(NOW));
        assertEquals(List.of("market:" + TICKER, "list"), client.calls);
    }
}

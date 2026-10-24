package com.ronrob.brti.market;

import java.io.IOException;
import java.util.List;

/** Read-only transport boundary; tests can supply deterministic responses without network access. */
public interface MarketDataClient {
    MarketPage listMarkets(long minCloseEpoch, long maxCloseEpoch, String cursor)
        throws IOException, InterruptedException;
    MarketContract getMarket(String ticker) throws IOException, InterruptedException;
    OrderBook getOrderBook(String ticker) throws IOException, InterruptedException;

    record MarketPage(List<MarketContract> markets, String cursor) {
        public MarketPage {
            markets = List.copyOf(markets);
        }
    }
}

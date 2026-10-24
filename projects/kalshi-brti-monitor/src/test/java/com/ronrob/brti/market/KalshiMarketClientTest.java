package com.ronrob.brti.market;

import static com.ronrob.brti.market.MarketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KalshiMarketClientTest {

    private HttpServer server;
    private KalshiMarketClient client;
    private volatile String response = "{}";
    private volatile int status = 200;
    private volatile String method;
    private volatile URI requested;

    @BeforeEach
    void startFixtureServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method = exchange.getRequestMethod();
            requested = exchange.getRequestURI();
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        client = new KalshiMarketClient(
            new ObjectMapper(),
            HttpClient.newHttpClient(),
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/trade-api/v2")
        );
    }

    @AfterEach
    void stopFixtureServer() {
        server.stop(0);
    }

    @Test
    void sendsBoundedGetRequestsAndEncodesTheCursor() throws Exception {
        response = """
        {"markets":[{"ticker":"KXBTC15M-TEST-1200","status":"active","floor_strike":"80000.25",
        "close_time":"2026-10-24T12:15:00Z","yes_ask_dollars":"0.60","volume_fp":12}],"cursor":"next"}
        """;
        var page = client.listMarkets(100, 200, "a+b /?");
        assertEquals("GET", method);
        assertEquals("/trade-api/v2/markets", requested.getPath());
        assertEquals(
            "series_ticker=KXBTC15M&min_close_ts=100&max_close_ts=200&limit=1000&cursor=a%2Bb+%2F%3F",
            requested.getRawQuery()
        );
        assertEquals("next", page.cursor());
        assertEquals(decimal("80000.25"), page.markets().get(0).priceToBeat());
        assertEquals(decimal("12"), page.markets().get(0).volumeFp());
    }

    @Test
    void sortsValidBidsAndIgnoresMalformedLevels() throws Exception {
        response = """
        {"orderbook_fp":{"yes_dollars":[["0.20","5"],["0.55","12"],["bad","1"],[-1,2],[1.2,2],[0.9,0],[0.3]],
        "no_dollars":[["0.40","7"]]}}
        """;
        var book = client.getOrderBook(TICKER);
        assertEquals("depth=10", requested.getQuery());
        assertEquals(2, book.yesBids().size());
        assertEquals(decimal("0.55"), book.bestYesBid().priceDollars());
        assertEquals(decimal("7"), book.bestNoBid().contracts());
        assertThrows(UnsupportedOperationException.class, () -> book.yesBids().clear());
    }

    @Test
    void permitsEmptyDepthButRejectsMalformedApiShapes() throws Exception {
        response = "{\"orderbook_fp\":{}}";
        assertTrue(client.getOrderBook(TICKER).yesBids().isEmpty());
        response = "{\"orderbook_fp\":{\"yes_dollars\":{}}}";
        assertThrows(IOException.class, () -> client.getOrderBook(TICKER));
        response = "{}";
        assertThrows(IOException.class, () -> client.getOrderBook(TICKER));
        assertThrows(IOException.class, () -> client.listMarkets(0, 1, null));
    }

    @Test
    void handlesMissingNumbersWithoutInventingAStrike() throws Exception {
        response =
            "{\"market\":{\"ticker\":\"KXBTC15M-TEST-1200\",\"floor_strike\":\"unavailable\"}}";
        assertNull(client.getMarket(TICKER).priceToBeat());
    }

    @Test
    void reportsHttpStatusWithoutTheResponseBody() {
        status = 503;
        response = "private diagnostic response";
        var error = assertThrows(IOException.class, () -> client.getMarket(TICKER));
        assertEquals("HTTP 503", error.getMessage());
    }
}

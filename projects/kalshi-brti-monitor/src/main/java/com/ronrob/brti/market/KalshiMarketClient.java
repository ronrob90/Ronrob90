package com.ronrob.brti.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Owns HTTP requests and vendor JSON parsing. No credentials, signing, or order endpoints. */
@Component
public class KalshiMarketClient implements MarketDataClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final int PAGE_SIZE = 1000;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI base;

    @Autowired
    public KalshiMarketClient(ObjectMapper mapper) {
        this(
            mapper,
            HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
            URI.create("https://external-api.kalshi.com/trade-api/v2")
        );
    }

    KalshiMarketClient(ObjectMapper mapper, HttpClient http, URI base) {
        this.mapper = mapper;
        this.http = http;
        this.base = base;
    }

    @Override
    public MarketPage listMarkets(long minCloseEpoch, long maxCloseEpoch, String cursor)
        throws IOException, InterruptedException {
        String path =
            "/markets?series_ticker=" +
            MarketContract.SERIES +
            "&min_close_ts=" +
            minCloseEpoch +
            "&max_close_ts=" +
            maxCloseEpoch +
            "&limit=" +
            PAGE_SIZE;
        if (cursor != null) path += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        JsonNode response = get(path);
        JsonNode entries = response.path("markets");
        if (!entries.isArray()) throw new IOException("Markets API returned no markets array");
        List<MarketContract> markets = new ArrayList<>();
        for (JsonNode entry : entries) markets.add(parseMarket(entry));
        return new MarketPage(markets, response.path("cursor").asText(""));
    }

    @Override
    public MarketContract getMarket(String ticker) throws IOException, InterruptedException {
        return parseMarket(get("/markets/" + ticker).path("market"));
    }

    @Override
    public OrderBook getOrderBook(String ticker) throws IOException, InterruptedException {
        JsonNode book = get("/markets/" + ticker + "/orderbook?depth=10").path("orderbook_fp");
        if (!book.isObject()) throw new IOException(
            "Orderbook API returned no orderbook_fp object"
        );
        return new OrderBook(
            parseLevels(book.path("yes_dollars")),
            parseLevels(book.path("no_dollars"))
        );
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base.toString() + path))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    private static MarketContract parseMarket(JsonNode market) {
        return new MarketContract(
            market.path("ticker").asText(),
            market.path("title").asText(),
            market.path("status").asText(),
            market.path("open_time").asText(),
            market.path("close_time").asText(),
            number(market.get("floor_strike")),
            number(market.get("yes_ask_dollars")),
            number(market.get("no_ask_dollars")),
            number(market.get("volume_fp")),
            number(market.get("open_interest_fp")),
            market.path("updated_time").asText(null)
        );
    }

    private static List<OrderBook.BookLevel> parseLevels(JsonNode entries) throws IOException {
        List<OrderBook.BookLevel> levels = new ArrayList<>();
        if (entries.isMissingNode() || entries.isNull()) return levels;
        if (!entries.isArray()) throw new IOException("Orderbook levels malformed");
        for (JsonNode level : entries) {
            if (!level.isArray() || level.size() < 2) continue;
            BigDecimal price = number(level.get(0));
            BigDecimal size = number(level.get(1));
            if (
                price == null ||
                size == null ||
                price.signum() < 0 ||
                price.compareTo(BigDecimal.ONE) > 0 ||
                size.signum() <= 0
            ) continue;
            levels.add(new OrderBook.BookLevel(price, size));
        }
        return levels;
    }

    private static BigDecimal number(JsonNode value) {
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

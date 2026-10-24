package com.ronrob.brti.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only Kalshi BTC 15-minute market discovery and quote monitor.
 * Uses public GET endpoints; NEVER signs orders or accepts trading instructions.
 * Polling snapshots are not an exchange tick stream. Always inspect timestamps.
 */
@Service
public class KalshiMarketService {
    static final String BASE = "https://external-api.kalshi.com/trade-api/v2";
    private static final String SERIES = "KXBTC15M";
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final Duration WINDOW = Duration.ofMinutes(15);
    // Kalshi paginates /markets; do not assume the first page contains the live contract.
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_DISCOVERY_PAGES = 10;


    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper;
    private final BrtiTracker brti;
    private volatile Map<String, Object> latest = unavailable("STARTING", "Waiting for first Kalshi market request");
    // Cache only the market identifier, not a quote. Quotes are freshly fetched every poll.
    private volatile String cachedTicker;
    private volatile Instant cachedCloseTime;

    public KalshiMarketService(ObjectMapper mapper, BrtiTracker brti) {
        this.mapper = mapper;
        this.brti = brti;
    }

    @Scheduled(initialDelay = 1200, fixedDelayString = "${kalshi.market.poll-ms:5000}")
    public void refresh() {
        try {
            Instant pollStarted = Instant.now();
            Instant now = pollStarted;
            // Reuse the identifier within its 15-minute window; discover on startup
            // or rollover. We never reuse a price or order book from the cache.
            JsonNode m = lookupCachedCurrentMarket(now);
            if (m == null) {
                m = discoverCurrentMarket(now);
            }
            if (m == null) return; // discoverCurrentMarket published a safe error state

            String ticker = m.path("ticker").asText();
            // Reject unknown ticker patterns rather than incorporating arbitrary paths.
            if (!ticker.matches("KXBTC15M-[A-Za-z0-9-]{4,100}")) {
                clear("INVALID_MARKET", "Active ticker did not match the expected BTC15M series");
                return;
            }
            BigDecimal strike = number(m.get("floor_strike"));
            if (strike == null || strike.signum() <= 0) {
                clear("STRIKE_UNAVAILABLE", "Kalshi has not yet published this window's floor_strike");
                return;
            }
            cachedTicker = ticker;
            cachedCloseTime = parseInstant(m.path("close_time").asText(null));

            JsonNode bookRoot = get("/markets/" + ticker + "/orderbook?depth=10");
            JsonNode book = bookRoot.path("orderbook_fp");
            if (!book.isObject()) throw new IOException("Orderbook API returned no orderbook_fp object");
            List<BookLevel> yesLevels = parseLevels(book.path("yes_dollars"));
            List<BookLevel> noLevels = parseLevels(book.path("no_dollars"));
            BookLevel yesBid = yesLevels.isEmpty() ? null : yesLevels.get(0);
            BookLevel noBid = noLevels.isEmpty() ? null : noLevels.get(0);
            BigDecimal bestYesAsk = noBid == null ? null : BigDecimal.ONE.subtract(noBid.price());
            BigDecimal bestNoAsk = yesBid == null ? null : BigDecimal.ONE.subtract(yesBid.price());

            // Re-fetch the market AFTER obtaining the orderbook. Discovery/cache
            // quote fields and the book can be sampled seconds apart; compare with
            // newer public reference quotes rather than assuming the list is current.
            JsonNode reference = get("/markets/" + ticker).path("market");
            if (!ticker.equals(reference.path("ticker").asText())) {
                clear("REFERENCE_MARKET_MISMATCH", "Reference market response did not match the selected ticker");
                return;
            }
            if (!isCurrentWindowMarket(reference, Instant.now())) {
                cachedTicker = null;
                cachedCloseTime = null;
                clear("MARKET_NOT_ACTIVE", "Selected market is no longer in the current 15-minute window");
                return;
            }
            BigDecimal referenceStrike = number(reference.get("floor_strike"));
            if (referenceStrike == null || referenceStrike.compareTo(strike) != 0) {
                cachedTicker = null;
                cachedCloseTime = null;
                clear("STRIKE_CHANGED", "Reference market price-to-beat differs from discovery response");
                return;
            }
            BigDecimal refYesAsk = number(reference.get("yes_ask_dollars"));
            BigDecimal refNoAsk = number(reference.get("no_ask_dollars"));
            Map<String,Object> quality = QuoteDecisionMath.assess(
                    yesBid == null ? null : yesBid.price(),
                    noBid == null ? null : noBid.price(),
                    bestYesAsk, bestNoAsk, refYesAsk, refNoAsk,
                    noBid == null ? null : noBid.size(),
                    yesBid == null ? null : yesBid.size());
            Instant fetchedAt = Instant.now();
            long pollDurationMs = Duration.between(pollStarted, fetchedAt).toMillis();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("state", "OPEN");
            result.put("error", null);
            result.put("seriesTicker", SERIES);
            result.put("ticker", ticker);
            result.put("title", m.path("title").asText());
            result.put("openTime", m.path("open_time").asText());
            result.put("closeTime", m.path("close_time").asText());
            result.put("priceToBeat", strike);
            result.put("yesBidDollars", yesBid == null ? null : yesBid.price());
            result.put("yesBidSize", yesBid == null ? null : yesBid.size());
            result.put("yesAskDollars", bestYesAsk);
            result.put("yesAskSize", noBid == null ? null : noBid.size());
            result.put("noBidDollars", noBid == null ? null : noBid.price());
            result.put("noBidSize", noBid == null ? null : noBid.size());
            result.put("noAskDollars", bestNoAsk);
            result.put("noAskSize", yesBid == null ? null : yesBid.size());
            result.put("yesBids", yesLevels.stream().map(BookLevel::toMap).toList());
            result.put("noBids", noLevels.stream().map(BookLevel::toMap).toList());
            result.put("marketYesAskDollars", refYesAsk);
            result.put("marketNoAskDollars", refNoAsk);
            result.put("referenceUpdatedTime", reference.path("updated_time").asText(null));
            result.put("referenceFetchedAt", fetchedAt.toString());
            result.put("quoteQuality", quality);
            result.put("quoteFeedsAligned", quality.get("canEvaluateQuotes"));
            result.put("pollDurationMs", pollDurationMs);
            result.put("volumeFp", number(m.get("volume_fp")));
            result.put("openInterestFp", number(m.get("open_interest_fp")));
            result.put("fetchedAt", fetchedAt.toString());
            result.put("fetchedAtEpochMs", fetchedAt.toEpochMilli());
            result.put("warning", "Read-only REST (~5s polling), not guaranteed fills. Market and book requests are asynchronous; quote mismatches block trade assessment. Break-even uses an illustrative general taker fee, not an approved order preview. No calibrated win probability.");
            // A fresh market strike replaces any previous manual target.
            brti.setStrike(strike);
            latest = result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            clear("ERROR", "Market polling interrupted");
        } catch (Exception ex) {
            // Never leak source headers, token information or stack traces through MCP.
            clear("ERROR", "Public Kalshi market API request failed: " + safeError(ex));
        }
    }

    /**
     * For this rolling 15-minute series, the contract's window is determined
     * from close_time, not from the time the market first started trading.
     * A future contract may already be tradable before its 15-minute window.
     */
    static boolean isCurrentWindowMarket(JsonNode m, Instant now) {
        String status = m.path("status").asText("");
        if (!"open".equals(status) && !"active".equals(status)) return false;
        Instant close = parseInstant(m.path("close_time").asText(null));
        if (close == null) return false;
        Instant open = parseInstant(m.path("open_time").asText(null));
        return !now.isBefore(close.minus(WINDOW)) && now.isBefore(close)
                && (open == null || !now.isBefore(open));
    }

    private JsonNode lookupCachedCurrentMarket(Instant now) throws IOException, InterruptedException {
        String ticker = cachedTicker;
        Instant close = cachedCloseTime;
        if (ticker == null || close == null || now.isBefore(close.minus(WINDOW)) || !now.isBefore(close)) {
            return null;
        }
        JsonNode market = get("/markets/" + ticker).path("market");
        if (ticker.equals(market.path("ticker").asText()) && isCurrentWindowMarket(market, now)) {
            return market;
        }
        cachedTicker = null;
        cachedCloseTime = null;
        return null;
    }

    /**
     * Use close-time bounded, unfiltered market discovery. The public API may
     * return status=active markets but zero markets for status=open; requesting
     * by time returned active/initialized/finalized candidates in the user's
     * diagnostic test. Select only the contract actually ACTIVE in the current
     * 15-minute period. Avoid arbitrary or invented price-to-beat values.
     */
    private JsonNode discoverCurrentMarket(Instant now) throws IOException, InterruptedException {
        String cursor = null;
        Set<String> visitedCursors = new HashSet<>();
        List<JsonNode> matching = new ArrayList<>();
        int marketsScanned = 0;
        int pagesScanned = 0;
        boolean complete = false;

        // Align to current UTC 15-minute interval. Query this and the next
        // interval to accommodate rollover without requiring status=open.
        long currentWindowStart = Math.floorDiv(now.getEpochSecond(), WINDOW.getSeconds())
                * WINDOW.getSeconds();
        long minClose = currentWindowStart;
        long maxClose = currentWindowStart + 2 * WINDOW.getSeconds();

        for (int page = 0; page < MAX_DISCOVERY_PAGES; page++) {
            String path = "/markets?series_ticker=" + SERIES
                    + "&min_close_ts=" + minClose
                    + "&max_close_ts=" + maxClose
                    + "&limit=" + PAGE_SIZE;
            if (cursor != null) {
                path += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
            }
            JsonNode response = get(path);
            JsonNode markets = response.path("markets");
            if (!markets.isArray()) throw new IOException("Markets API returned no markets array");
            pagesScanned++;

            for (JsonNode market : markets) {
                marketsScanned++;
                if (isCurrentWindowMarket(market, now)) matching.add(market);
            }

            String next = response.path("cursor").asText("");
            if (next.isBlank()) {
                complete = true;
                break;
            }
            if (!visitedCursors.add(next)) throw new IOException("Market pagination repeated a cursor");
            cursor = next;
        }

        if (!complete) {
            clear("DISCOVERY_INCOMPLETE", "Stopped after " + pagesScanned +
                    " pages (" + marketsScanned + " records); could not verify unique current market");
            return null;
        }
        if (matching.size() != 1) {
            clear("NO_UNAMBIGUOUS_MARKET", "Found " + matching.size() + " matching " + SERIES +
                    " 15-minute windows after scanning " + marketsScanned + " records across " +
                    pagesScanned + " pages");
            return null;
        }
        return matching.get(0);
    }

    public Map<String, Object> marketSnapshot() {
        Map<String, Object> copy = new LinkedHashMap<>(latest);
        Instant now = Instant.now();
        Object t = copy.get("fetchedAtEpochMs");
        Long age = t instanceof Long v ? Math.max(0, System.currentTimeMillis() - v) : null;
        Instant close = parseInstant((String) copy.get("closeTime"));
        boolean fresh = "OPEN".equals(copy.get("state")) && age != null && age < 15000
                && close != null && now.isBefore(close);
        copy.remove("fetchedAtEpochMs");
        copy.put("dataAgeMs", age);
        copy.put("marketFresh", fresh);
        long secondsRemaining = close == null ? 0 : Math.max(0, Duration.between(now, close).toSeconds());
        copy.put("secondsToClose", close == null ? null : secondsRemaining);
        Object pollMs = copy.get("pollDurationMs");
        // A quote may be valid for observing the market but unsafe to assess as an entry.
        boolean quoteReady = fresh && age <= 5000 && secondsRemaining > 15
                && pollMs instanceof Long duration && duration <= 2500
                && Boolean.TRUE.equals(copy.get("quoteFeedsAligned"));
        copy.put("quoteReadyForAnalysis", quoteReady);
        return copy;
    }

    public Map<String, Object> combinedSnapshot() {
        Map<String, Object> market = marketSnapshot();
        Map<String, Object> index = brti.snapshot();
        Map<String, Object> combined = new LinkedHashMap<>();
        combined.put("market", market);
        combined.put("brti", index);
        boolean ready = Boolean.TRUE.equals(market.get("marketFresh"))
                && Boolean.TRUE.equals(index.get("dataFresh"))
                && market.get("priceToBeat") != null;
        combined.put("validForSettlementComparison", ready);
        boolean tradeReviewReady = ready && Boolean.TRUE.equals(market.get("quoteReadyForAnalysis"));
        combined.put("quoteAndSettlementDataReadyForAnalysis", tradeReviewReady);
        // This is NOT a trade signal: probability is uncalibrated and fee overrides
        // and executable fills are unknown. ALWAYS leave decision at NO_TRADE.
        combined.put("tradeDecision", "NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED");
        combined.put("estimatedWinProbability", null);
        combined.put("expectedValue", null);
        combined.put("warning", ready
                ? "Live index and market quote captured independently; timestamps differ, prices may change. Settlement requires official final-minute BRTI average."
                : "Missing or stale BRTI/market/strike; do NOT use this for trade decisions.");
        return combined;
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(TIMEOUT).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    private synchronized void clear(String status, String error) {
        brti.clearStrike();
        latest = unavailable(status, error);
    }

    private static Map<String, Object> unavailable(String state, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("error", error);
        result.put("seriesTicker", SERIES);
        result.put("priceToBeat", null);
        result.put("yesAskDollars", null);
        result.put("noAskDollars", null);
        result.put("marketFresh", false);
        return result;
    }

    static List<BookLevel> parseLevels(JsonNode entries) throws IOException {
        List<BookLevel> levels = new ArrayList<>();
        if (entries.isMissingNode() || entries.isNull()) return levels;
        if (!entries.isArray()) throw new IOException("Orderbook levels malformed");
        for (JsonNode level : entries) {
            if (!level.isArray() || level.size() < 2) continue;
            BigDecimal price = number(level.get(0));
            BigDecimal size = number(level.get(1));
            if (price == null || size == null || price.signum() < 0
                    || price.compareTo(BigDecimal.ONE) > 0 || size.signum() <= 0) continue;
            levels.add(new BookLevel(price, size));
        }
        levels.sort(Comparator.comparing(BookLevel::price).reversed());
        return List.copyOf(levels);
    }

    static BigDecimal number(JsonNode value) {
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try { return new BigDecimal(value.asText()); }
        catch (NumberFormatException ex) { return null; }
    }

    static Instant parseInstant(String value) {
        try { return value == null || value.isBlank() ? null : Instant.parse(value); }
        catch (Exception ex) { return null; }
    }

    private static String safeError(Exception ex) {
        if (ex instanceof java.net.http.HttpTimeoutException) return "timeout";
        if (ex.getMessage() != null && ex.getMessage().matches("HTTP [0-9]{3}")) return ex.getMessage();
        return ex.getClass().getSimpleName();
    }

    record BookLevel(BigDecimal price, BigDecimal size) {
        Map<String, Object> toMap() {
            return Map.of("priceDollars", price, "contracts", size);
        }
    }
}

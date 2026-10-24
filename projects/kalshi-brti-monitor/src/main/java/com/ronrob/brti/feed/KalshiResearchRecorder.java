package com.ronrob.brti.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Local, read-only research sampler: writes BRTI observations and market quotes to CSV.
 * This is a ~1-second sampled journal, NOT an authoritative every-tick feed.
 * No credential, user position, account data, or order instructions are recorded.
 * To disable, set kalshi.research.enabled=false in application.properties.
 */
@Component
public class KalshiResearchRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(KalshiResearchRecorder.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("uuuuMMdd").withZone(ZoneOffset.UTC);
    private static final String HEADER = "observed_at_utc,contract_ticker,price_to_beat,brti,trailing_60s_average,"
            + "final_minute_average,final_minute_count,yes_book_ask,no_book_ask,"
            + "market_yes_ask,market_no_ask,quote_status,quote_age_ms,seconds_to_close\n";
    private final BrtiTracker tracker;
    private final KalshiMarketService markets;
    private String lastObserved;
    private boolean warned;

    public KalshiResearchRecorder(BrtiTracker tracker, KalshiMarketService markets) {
        this.tracker = tracker;
        this.markets = markets;
    }

    @Scheduled(initialDelay = 4000, fixedDelayString = "${kalshi.research.sample-ms:1000}")
    public synchronized void sample() {
        Map<String, Object> b = tracker.snapshot();
        String observed = (String) b.get("observedAt");
        if (observed == null || !Boolean.TRUE.equals(b.get("dataFresh")) || observed.equals(lastObserved)) return;
        Map<String, Object> m = markets.marketSnapshot();
        boolean marketFresh = Boolean.TRUE.equals(m.get("marketFresh"));
        @SuppressWarnings("unchecked")
        Map<String, Object> quality = m.get("quoteQuality") instanceof Map<?, ?> q
                ? (Map<String, Object>) q : Map.of();
        String row = String.join(",",
                csv(observed),
                csv(marketFresh ? m.get("ticker") : null),
                cell(marketFresh ? m.get("priceToBeat") : null),
                cell(b.get("brti")),
                cell(b.get("trailing60sAverage")),
                cell(b.get("finalMinuteAverage")),
                cell(b.get("finalMinuteCount")),
                cell(marketFresh ? m.get("yesAskDollars") : null),
                cell(marketFresh ? m.get("noAskDollars") : null),
                cell(marketFresh ? m.get("marketYesAskDollars") : null),
                cell(marketFresh ? m.get("marketNoAskDollars") : null),
                csv(marketFresh ? quality.get("status") : null),
                cell(marketFresh ? m.get("dataAgeMs") : null),
                cell(marketFresh ? m.get("secondsToClose") : null)) + "\n";
        try {
            Path directory = Path.of(System.getProperty("user.home"), "KalshiResearch");
            Files.createDirectories(directory);
            Path file = directory.resolve("brti-market-" + DAY.format(Instant.parse(observed)) + ".csv");
            if (!Files.exists(file) || Files.size(file) == 0) {
                Files.writeString(file, HEADER, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            Files.writeString(file, row, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            lastObserved = observed;
            warned = false;
        } catch (IOException | RuntimeException e) {
            if (!warned) {
                LOG.warn("Could not record Kalshi market research data locally (BRTI monitoring unaffected): {}", e.getClass().getSimpleName());
                warned = true;
            }
        }
    }

    private static String cell(Object o) { return o == null ? "" : o.toString(); }
    private static String csv(Object o) {
        if (o == null) return "";
        return "\"" + o.toString().replace("\"", "\"\"").replace("\n", " ") + "\"";
    }
}

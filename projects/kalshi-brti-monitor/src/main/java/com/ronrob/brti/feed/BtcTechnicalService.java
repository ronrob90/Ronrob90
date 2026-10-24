package com.ronrob.brti.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Public Kraken BTC/USD spot OHLC technical context, 100% read-only.
 * Runs in a separate daemon worker, so delayed OHLC requests cannot stall the
 * BRTI websocket, Kalshi market poller, or 1-second research recorder.
 */
@Service
public class BtcTechnicalService {

    private static final Logger LOG = LoggerFactory.getLogger(BtcTechnicalService.class);
    private static final String BASE = "https://api.kraken.com/0/public/OHLC?pair=XBTUSD&interval=";
    private static final int[] MINUTES = { 1, 5, 15, 60, 240, 1440 };
    private static final String[] LABELS = { "1m", "5m", "15m", "1h", "4h", "1d" };
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("uuuuMMdd").withZone(
        ZoneOffset.UTC
    );
    private static final String JOURNAL_HEADER =
        "closed_at_utc,exchange,spot_close,ema8,ema21,ema50,rsi14,atr14,adx14,plus_di14,minus_di14,rolling_vwap20,trend,momentum\n";

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    private final Map<String, Map<String, Object>> latest = new ConcurrentHashMap<>();
    private ScheduledExecutorService worker;
    private long polls;
    private String lastJournaledCandle;
    private boolean warnedJournal;

    public BtcTechnicalService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void start() {
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kraken-spot-ta-readonly");
            t.setDaemon(true);
            return t;
        });
        // First pass fetches all six timeframes. Later calls are staggered.
        worker.scheduleWithFixedDelay(this::updateSafely, 3, 25, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (worker != null) worker.shutdownNow();
    }

    private void updateSafely() {
        try {
            update();
        } catch (Exception ex) {
            LOG.warn("BTC technical polling error: {}", ex.getClass().getSimpleName());
        }
    }

    private void update() {
        long run = polls++;
        for (int i = 0; i < MINUTES.length; i++) {
            // 1m every ~25s; 5m ~50s; 15m ~75s; longer timeframes ~225s.
            int multiple = i == 0 ? 1 : i == 1 ? 2 : i == 2 ? 3 : 9;
            if (run % multiple != 0) continue;
            String label = LABELS[i];
            try {
                List<BtcTechnicalMath.Candle> candles = downloadClosedCandles(MINUTES[i]);
                Instant evaluatedAt = Instant.now();
                Map<String, Object> frame = BtcTechnicalMath.analyze(
                    candles,
                    MINUTES[i],
                    evaluatedAt
                );
                frame.put("fetchedAt", evaluatedAt.toString());
                frame.put("fetchedAtEpochMs", evaluatedAt.toEpochMilli());
                latest.put(label, frame);
                if (i == 0 && "READY".equals(frame.get("status"))) writeOneMinuteJournal(frame);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("status", "ERROR");
                error.put("fresh", false);
                error.put(
                    "error",
                    "Could not fetch/parse Kraken " +
                        label +
                        " candles: " +
                        ex.getClass().getSimpleName()
                );
                error.put("fetchedAt", Instant.now().toString());
                latest.put(label, error);
                LOG.warn(
                    "Kraken BTC/USD {} candle poll failed ({})",
                    label,
                    ex.getClass().getSimpleName()
                );
            }
        }
    }

    /** Exposed for isolated data-validation tests; rejects the forming last bar. */
    List<BtcTechnicalMath.Candle> downloadClosedCandles(int intervalMinutes)
        throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + intervalMinutes))
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json")
            .header("User-Agent", "KalshiBRTIMonitorResearch/0.4 (public OHLC, read-only)")
            .GET()
            .build();
        HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException(
            "Kraken HTTP " + response.statusCode()
        );
        JsonNode root = mapper.readTree(response.body());
        JsonNode errors = root.path("error");
        if (!errors.isArray() || !errors.isEmpty()) throw new IOException(
            "Kraken returned an API error"
        );
        JsonNode result = root.path("result");
        if (!result.isObject()) throw new IOException("Kraken result missing");
        JsonNode raw = null;
        for (Iterator<Map.Entry<String, JsonNode>> it = result.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> item = it.next();
            if (!"last".equals(item.getKey()) && item.getValue().isArray()) {
                if (raw != null) throw new IOException("Ambiguous Kraken pair");
                raw = item.getValue();
            }
        }
        if (raw == null || raw.size() < 55) throw new IOException("Too few OHLC bars");
        List<BtcTechnicalMath.Candle> candles = new ArrayList<>();
        long now = Instant.now().getEpochSecond();
        long durationSec = (long) intervalMinutes * 60;
        // Official Kraken OHLC response includes a final, uncommitted candle.
        // Always drop that final entry, even if the timestamp is unexpected.
        for (int i = 0; i < raw.size() - 1; i++) {
            JsonNode row = raw.get(i);
            if (!row.isArray() || row.size() < 8) throw new IOException("Malformed Kraken candle");
            try {
                long ts = Long.parseLong(row.get(0).asText());
                double open = Double.parseDouble(row.get(1).asText());
                double high = Double.parseDouble(row.get(2).asText());
                double low = Double.parseDouble(row.get(3).asText());
                double close = Double.parseDouble(row.get(4).asText());
                double vwap = Double.parseDouble(row.get(5).asText());
                double vol = Double.parseDouble(row.get(6).asText());
                long count = Long.parseLong(row.get(7).asText());
                if (
                    ts <= 0 ||
                    ts + durationSec > now ||
                    !Double.isFinite(open) ||
                    !Double.isFinite(high) ||
                    !Double.isFinite(low) ||
                    !Double.isFinite(close) ||
                    !Double.isFinite(vwap) ||
                    !Double.isFinite(vol) ||
                    low <= 0 ||
                    low > high ||
                    open < low ||
                    open > high ||
                    close < low ||
                    close > high ||
                    vwap < 0 ||
                    vol < 0 ||
                    count < 0
                ) {
                    continue; // Ignore invalid or not-yet-finalized provider data.
                }
                candles.add(
                    new BtcTechnicalMath.Candle(ts, open, high, low, close, vwap, vol, count)
                );
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid OHLC numeric field", ex);
            }
        }
        candles.sort(Comparator.comparingLong(BtcTechnicalMath.Candle::startEpochSeconds));
        // Reject duplicate / out-of-sequence candle timestamps; do not manufacture candles.
        long prev = -1;
        for (BtcTechnicalMath.Candle c : candles) {
            if (c.startEpochSeconds() <= prev) throw new IOException("Duplicate OHLC candle");
            prev = c.startEpochSeconds();
        }
        if (candles.size() < 55) throw new IOException("Insufficient finalized Kraken candles");
        return candles;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> timeframes = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < LABELS.length; i++) {
            String label = LABELS[i];
            Map<String, Object> frame = new LinkedHashMap<>(
                latest.getOrDefault(
                    label,
                    Map.of(
                        "status",
                        "INITIALIZING",
                        "fresh",
                        false,
                        "error",
                        "Waiting for first Kraken " + label + " fetch"
                    )
                )
            );
            Object t = frame.remove("fetchedAtEpochMs");
            Long age = t instanceof Long v ? Math.max(0, now - v) : null;
            long maxPollAgeMs = i == 0 ? 90_000L : i == 1 ? 120_000L : i == 2 ? 180_000L : 450_000L;
            boolean fresh =
                "READY".equals(frame.get("status")) &&
                Boolean.TRUE.equals(frame.get("fresh")) &&
                age != null &&
                age <= maxPollAgeMs;
            // Account for time passing after analysis without waiting for another poll.
            Object end = frame.get("lastClosedCandleEndEpochSeconds");
            if (end instanceof Long closedEpoch) {
                long elapsed = Math.max(0, Instant.now().getEpochSecond() - closedEpoch);
                frame.put("secondsSinceLastClose", elapsed);
                if (elapsed > Math.max(150L, (long) MINUTES[i] * 120L + 30L)) fresh = false;
            }
            frame.put("fresh", fresh);
            if (!fresh && "READY".equals(frame.get("status"))) frame.put("status", "STALE");
            frame.put("pollAgeMs", age);
            timeframes.put(label, frame);
        }
        body.put("source", "Kraken Spot REST OHLC public API");
        body.put("symbol", "BTC/USD (Kraken XBTUSD; USD quote)");
        body.put("fetchedAtUtc", Instant.now().toString());
        body.put("timeframes", timeframes);
        boolean core =
            ready(timeframes, "1m") && ready(timeframes, "5m") && ready(timeframes, "15m");
        body.put("technicalContextReady", core);
        body.put("tradeDecision", "NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED");
        body.put("estimatedWinProbability", null);
        body.put(
            "warning",
            "Kraken BTC/USD spot OHLC is NOT CF Benchmarks BRTI. Uses CLOSED bars only, polled every ~25-225s. " +
                "Spot TA cannot determine Kalshi settlement or an edge. No calibrated probability, trade automation, or guaranteed fills."
        );
        return body;
    }

    private static boolean ready(Map<String, Object> frames, String key) {
        return Boolean.TRUE.equals(((Map<?, ?>) frames.get(key)).get("fresh"));
    }

    private void writeOneMinuteJournal(Map<String, Object> frame) {
        String closed = (String) frame.get("lastClosedCandleEndUtc");
        if (closed == null || closed.equals(lastJournaledCandle)) return;
        try {
            Path folder = Path.of(System.getProperty("user.home"), "KalshiResearch");
            Files.createDirectories(folder);
            Path path = folder.resolve("btc-ta-" + DAY.format(Instant.parse(closed)) + ".csv");
            if (!Files.exists(path) || Files.size(path) == 0) {
                Files.writeString(
                    path,
                    JOURNAL_HEADER,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            }
            StringBuilder row = new StringBuilder();
            Object[] fields = {
                closed,
                "Kraken BTC/USD",
                frame.get("close"),
                frame.get("ema8"),
                frame.get("ema21"),
                frame.get("ema50"),
                frame.get("rsi14"),
                frame.get("atr14"),
                frame.get("adx14"),
                frame.get("plusDi14"),
                frame.get("minusDi14"),
                frame.get("rollingVwap20ClosedBars"),
                frame.get("trendStructure"),
                frame.get("momentumContext"),
            };
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) row.append(',');
                row.append(fields[i] == null ? "" : fields[i].toString());
            }
            row.append('\n');
            Files.writeString(
                path,
                row.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            lastJournaledCandle = closed;
            warnedJournal = false;
        } catch (IOException | RuntimeException ex) {
            if (!warnedJournal) {
                LOG.warn(
                    "BTC TA research CSV write failed; live monitoring unaffected: {}",
                    ex.getClass().getSimpleName()
                );
                warnedJournal = true;
            }
        }
    }
}

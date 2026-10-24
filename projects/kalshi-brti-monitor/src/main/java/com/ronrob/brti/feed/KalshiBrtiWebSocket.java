package com.ronrob.brti.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronrob.brti.crypto.KalshiSigner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only authenticated subscription to CF Benchmarks BRTI via Kalshi. */
@Component
public class KalshiBrtiWebSocket {
    private static final Logger LOG = LoggerFactory.getLogger(KalshiBrtiWebSocket.class);
    private static final String SIGN_PATH = "/trade-api/ws/v2";
    private static final String SUBSCRIBE = "{\"id\":1,\"cmd\":\"subscribe\",\"params\":{\"channels\":[\"cfbenchmarks_value\"],\"index_ids\":[\"BRTI\"]}}";
    private final BrtiTracker tracker;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "kalshi-brti-connection");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean reconnectQueued = new AtomicBoolean(false);
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private volatile WebSocket socket;
    private volatile boolean stopping = false;

    public KalshiBrtiWebSocket(BrtiTracker tracker, ObjectMapper mapper) {
        this.tracker = tracker;
        this.mapper = mapper;
    }

    @PostConstruct
    public void start() {
        executor.schedule(this::connect, 0, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(() -> {
            WebSocket current = socket;
            if (!stopping && current != null && !current.isOutputClosed()) {
                current.sendPing(ByteBuffer.wrap(new byte[]{1})).exceptionally(error -> null);
            }
        }, 20, 20, TimeUnit.SECONDS);
    }

    private void connect() {
        if (stopping) return;
        String keyId = System.getenv("KALSHI_KEY_ID");
        String path = System.getenv("KALSHI_PRIVATE_KEY_PATH");
        if (keyId == null || keyId.isBlank() || path == null || path.isBlank()) {
            tracker.status("CONFIGURATION_REQUIRED", "Set KALSHI_KEY_ID and KALSHI_PRIVATE_KEY_PATH in IntelliJ Run Configuration.");
            return;
        }
        String environment = System.getenv().getOrDefault("KALSHI_ENV", "production");
        String url = "demo".equalsIgnoreCase(environment)
                ? "wss://external-api-ws.demo.kalshi.co/trade-api/ws/v2"
                : "wss://external-api-ws.kalshi.com/trade-api/ws/v2";
        tracker.status("CONNECTING", null);
        try {
            KalshiSigner signer = new KalshiSigner(Path.of(path));
            String timestamp = Long.toString(System.currentTimeMillis());
            String signature = signer.sign(timestamp, "GET", SIGN_PATH);
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .header("KALSHI-ACCESS-KEY", keyId)
                    .header("KALSHI-ACCESS-TIMESTAMP", timestamp)
                    .header("KALSHI-ACCESS-SIGNATURE", signature)
                    .buildAsync(URI.create(url), new Listener())
                    .whenComplete((connected, error) -> {
                        if (error != null) {
                            String msg = safeError(error);
                            LOG.warn("Kalshi WebSocket connection failed: {}", msg);
                            tracker.status("DISCONNECTED", msg);
                            scheduleReconnect();
                        } else {
                            socket = connected;
                        }
                    });
        } catch (Exception e) {
            String msg = safeError(e);
            LOG.warn("BRTI monitor cannot connect: {}", msg);
            tracker.status("DISCONNECTED", msg);
            scheduleReconnect();
        }
    }

    private String safeError(Throwable throwable) {
        // Don't serialize exceptions or credential-bearing HTTP headers into dashboard.
        String message = throwable.getMessage();
        if (message != null && message.contains("status code")) return message;
        return throwable.getClass().getSimpleName() + ": " + (message == null ? "connection failure" : message);
    }

    private void scheduleReconnect() {
        if (stopping || !reconnectQueued.compareAndSet(false, true)) return;
        executor.schedule(() -> {
            reconnectQueued.set(false);
            connect();
        }, 8, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        stopping = true;
        WebSocket current = socket;
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "Shutdown");
        executor.shutdownNow();
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            tracker.status("CONNECTED_WAITING_FOR_DATA", null);
            LOG.info("Kalshi socket connected; subscribing to BRTI.");
            webSocket.sendText(SUBSCRIBE, true);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                if (buffer.length() + data.length() > 1_000_000) {
                    buffer.setLength(0);
                    throw new IllegalStateException("Oversized WebSocket frame");
                }
                buffer.append(data);
                if (last) {
                    JsonNode node = mapper.readTree(buffer.toString());
                    buffer.setLength(0);
                    String type = node.path("type").asText();
                    if ("cfbenchmarks_value".equals(type)) {
                        tracker.accept(node);
                        tracker.status("STREAMING", null);
                    } else if ("error".equals(type)) {
                        // An auth/subscription/permission error may be returned as a frame.
                        LOG.warn("Kalshi feed error: {}", node);
                        tracker.status("SUBSCRIPTION_ERROR", node.toString());
                    } else if ("subscribed".equals(type)) {
                        LOG.info("BRTI subscription accepted; awaiting data.");
                    }
                }
            } catch (Exception exception) {
                LOG.warn("Error parsing BRTI frame: {}", exception.getMessage());
                buffer.setLength(0);
            } finally {
                webSocket.request(1);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (socket == webSocket) socket = null;
            tracker.status("DISCONNECTED", "WebSocket closed: " + statusCode + "; " + reason);
            scheduleReconnect();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (socket == webSocket) socket = null;
            tracker.status("DISCONNECTED", safeError(error));
            scheduleReconnect();
        }
    }
}

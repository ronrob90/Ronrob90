package com.ronrob.brti.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronrob.brti.feed.BrtiTracker;
import com.ronrob.brti.feed.BtcTechnicalService;
import com.ronrob.brti.feed.KalshiMarketService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal stateless, read-only MCP Streamable HTTP endpoint.
 * Exposes BRTI and public market metadata. It never accepts credentials or submits trades.
 * Bind Spring Boot to 127.0.0.1 as in the original application.properties.
 * This is a small compatibility adapter; validate against your tunnel client
 * before relying on it, and use an official MCP SDK for future expansion.
 */
@RestController
public class BrtiMcpController {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String TOOL_NAME = "get_brti_snapshot";
    private static final String MARKET_TOOL = "get_live_market_snapshot";
    private static final String INTELLIGENCE_TOOL = "get_btc_intelligence_snapshot";
    private final BrtiTracker tracker;
    private final KalshiMarketService markets;
    private final BtcTechnicalService technical;
    private final ObjectMapper mapper;

    public BrtiMcpController(
        BrtiTracker tracker,
        KalshiMarketService markets,
        BtcTechnicalService technical,
        ObjectMapper mapper
    ) {
        this.tracker = tracker;
        this.markets = markets;
        this.technical = technical;
        this.mapper = mapper;
    }

    @PostMapping(value = "/mcp", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> handle(@RequestBody JsonNode req) {
        if (!req.isObject() || !"2.0".equals(req.path("jsonrpc").asText())) {
            return ResponseEntity.badRequest().body(
                rpcError(null, -32600, "Invalid JSON-RPC request")
            );
        }

        JsonNode id = req.get("id");
        String method = req.path("method").asText();
        // MCP notifications do not receive JSON-RPC responses.
        if (id == null || id.isNull()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }

        return switch (method) {
            case "initialize" -> ResponseEntity.ok(
                rpcSuccess(
                    id,
                    Map.of(
                        "protocolVersion",
                        PROTOCOL_VERSION,
                        "capabilities",
                        Map.of("tools", Map.of("listChanged", false)),
                        "serverInfo",
                        Map.of("name", "kalshi-brti-readonly", "version", "0.1.0"),
                        "instructions",
                        "Market-data snapshots only. Quotes can be stale. No trading tools."
                    )
                )
            );
            case "ping" -> ResponseEntity.ok(rpcSuccess(id, Map.of()));
            case "tools/list" -> ResponseEntity.ok(
                rpcSuccess(
                    id,
                    Map.of(
                        "tools",
                        List.of(
                            Map.of(
                                "name",
                                TOOL_NAME,
                                "description",
                                "Get the most recent Kalshi CF Benchmarks BRTI price, timestamps, freshness, target and settlement averages. Read-only; never use stale readings for trade decisions.",
                                "inputSchema",
                                Map.of(
                                    "type",
                                    "object",
                                    "properties",
                                    Map.of(),
                                    "additionalProperties",
                                    false
                                )
                            ),
                            Map.of(
                                "name",
                                MARKET_TOOL,
                                "description",
                                "Get the active Kalshi KXBTC15M ticker, official price-to-beat, current YES/NO best executable asks derived from the public orderbook, bid depth, timestamps and live BRTI comparison. Read-only; refuse stale data; quotes not guaranteed fills.",
                                "inputSchema",
                                Map.of(
                                    "type",
                                    "object",
                                    "properties",
                                    Map.of(),
                                    "additionalProperties",
                                    false
                                )
                            ),
                            Map.of(
                                "name",
                                INTELLIGENCE_TOOL,
                                "description",
                                "Read-only snapshot of active Kalshi BTC15m contract, BRTI settlement index, " +
                                    "public YES/NO quote safety and fee estimates, plus multi-timeframe Kraken spot technical indicators " +
                                    "(1m/5m/15m/1h/4h/1d). Includes source/timestamps/freshness; Kraken spot is NOT BRTI. " +
                                    "No calibrated win probability, trading or order placement.",
                                "inputSchema",
                                Map.of(
                                    "type",
                                    "object",
                                    "properties",
                                    Map.of(),
                                    "additionalProperties",
                                    false
                                )
                            )
                        )
                    )
                )
            );
            case "tools/call" -> callTool(id, req.path("params"));
            default -> ResponseEntity.ok(rpcError(id, -32601, "Method not found: " + method));
        };
    }

    @GetMapping("/mcp")
    public ResponseEntity<?> noSse() {
        // Streamable HTTP permits 405 when optional GET-based SSE is unsupported.
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    private ResponseEntity<?> callTool(JsonNode id, JsonNode params) {
        String name = params.path("name").asText();
        if (
            !TOOL_NAME.equals(name) && !MARKET_TOOL.equals(name) && !INTELLIGENCE_TOOL.equals(name)
        ) {
            return ResponseEntity.ok(rpcError(id, -32602, "Unknown tool"));
        }
        Object snapshot;
        if (INTELLIGENCE_TOOL.equals(name)) {
            snapshot = BtcIntelligenceController.combinedReadOnly(markets, technical);
        } else if (MARKET_TOOL.equals(name)) {
            snapshot = markets.combinedSnapshot();
        } else {
            snapshot = tracker.snapshot();
        }
        try {
            String readableJson = mapper.writeValueAsString(snapshot);
            // Keep structured and text forms for differing client MCP versions.
            Map<String, Object> toolResult = new LinkedHashMap<>();
            toolResult.put("content", List.of(Map.of("type", "text", "text", readableJson)));
            toolResult.put("structuredContent", snapshot);
            toolResult.put("isError", false);
            return ResponseEntity.ok(rpcSuccess(id, toolResult));
        } catch (JsonProcessingException e) {
            return ResponseEntity.ok(rpcError(id, -32603, "Could not serialize snapshot"));
        }
    }

    private Map<String, Object> rpcSuccess(JsonNode id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> rpcError(JsonNode id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }
}

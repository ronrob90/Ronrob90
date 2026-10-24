package com.ronrob.brti.web;

import static com.ronrob.brti.market.MarketFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronrob.brti.feed.BrtiTracker;
import com.ronrob.brti.feed.BtcTechnicalService;
import com.ronrob.brti.feed.KalshiMarketService;
import com.ronrob.brti.market.MarketDiscovery;
import com.ronrob.brti.market.MarketFixtures.FakeClient;
import java.io.IOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Exercise Spring's actual response serialization without scheduling polls or opening live feeds. */
class MarketApiContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeClient client = new FakeClient();
    private KalshiMarketService markets;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var tracker = new BrtiTracker(mapper);
        var technical = new BtcTechnicalService(mapper);
        markets = new KalshiMarketService(
            client,
            new MarketDiscovery(client),
            tracker,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        mvc = MockMvcBuilders.standaloneSetup(
            new KalshiMarketController(markets),
            new BtcIntelligenceController(technical, markets),
            new BrtiMcpController(tracker, markets, technical, mapper)
        ).build();
        markets.refresh();
    }

    @Test
    void exposesFlatTypedMarketDataThroughRest() throws Exception {
        mvc.perform(get("/api/market/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("OPEN"))
            .andExpect(jsonPath("$.ticker").value(TICKER))
            .andExpect(jsonPath("$.yesAskDollars").value(0.60))
            .andExpect(jsonPath("$.quoteReadyForAnalysis").value(true))
            .andExpect(jsonPath("$.contract").doesNotExist())
            .andExpect(jsonPath("$.fetchedAtEpochMs").doesNotExist());
        mvc.perform(get("/api/intelligence/combined"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.market.ticker").value(TICKER))
            .andExpect(
                jsonPath("$.tradeDecision").value("NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED")
            );
    }

    @Test
    void keepsMcpTextAndStructuredPayloadsIdenticalForEveryTool() throws Exception {
        for (String tool : List.of(
            "get_brti_snapshot",
            "get_live_market_snapshot",
            "get_btc_intelligence_snapshot"
        )) {
            String request = """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"%s","arguments":{}}}
            """.formatted(tool);
            String response = mvc
                .perform(post("/mcp").contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
            JsonNode result = mapper.readTree(response).path("result");
            assertFalse(result.path("isError").asBoolean());
            assertTrue(result.path("structuredContent").isObject());
            assertEquals(
                result.get("structuredContent"),
                mapper.readTree(result.path("content").get(0).path("text").asText())
            );
        }
    }

    @Test
    void replacesAnOldSuccessfulResponseWithAnExplicitUnavailableState() throws Exception {
        client.failure = new IOException("HTTP 503");
        markets.refresh();
        mvc.perform(get("/api/market/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ERROR"))
            .andExpect(jsonPath("$.marketFresh").value(false))
            .andExpect(jsonPath("$.quoteReadyForAnalysis").value(false));
    }
}

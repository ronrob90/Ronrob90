package com.ronrob.brti.web;

import com.ronrob.brti.feed.BtcTechnicalService;
import com.ronrob.brti.feed.KalshiMarketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Local-only read-only REST endpoints. Does not accept account/order instructions. */
@RestController
@RequestMapping("/api/intelligence")
public class BtcIntelligenceController {
    private final BtcTechnicalService technical;
    private final KalshiMarketService kalshi;

    public BtcIntelligenceController(BtcTechnicalService technical, KalshiMarketService kalshi) {
        this.technical = technical;
        this.kalshi = kalshi;
    }

    @GetMapping("/technical")
    public Map<String, Object> technical() { return technical.snapshot(); }

    @GetMapping("/combined")
    public Map<String, Object> combined() { return combinedReadOnly(kalshi, technical); }

    public static Map<String, Object> combinedReadOnly(KalshiMarketService markets, BtcTechnicalService technical) {
        Map<String, Object> result = new LinkedHashMap<>(markets.combinedSnapshot());
        Map<String, Object> ta = technical.snapshot();
        result.put("technical", ta);
        result.put("combinedContextReady", Boolean.TRUE.equals(result.get("validForSettlementComparison"))
                && Boolean.TRUE.equals(ta.get("technicalContextReady")));
        result.put("tradeDecision", "NO_TRADE_UNTIL_PROBABILITY_MODEL_VALIDATED");
        result.put("estimatedWinProbability", null);
        result.put("expectedValue", null);
        result.put("technicalWarning", "Kraken spot indicators are context only; BRTI determines official Kalshi settlement. " +
                "No calibrated win probability or approved executable quote. NEVER infer positive EV from TA confluence alone.");
        return result;
    }
}

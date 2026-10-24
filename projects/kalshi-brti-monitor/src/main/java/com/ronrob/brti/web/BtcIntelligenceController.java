package com.ronrob.brti.web;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ronrob.brti.feed.BtcTechnicalService;
import com.ronrob.brti.feed.KalshiMarketService;
import com.ronrob.brti.market.CombinedMarketSnapshot;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Map<String, Object> technical() {
        return technical.snapshot();
    }

    @GetMapping("/combined")
    public IntelligenceSnapshot combined() {
        return combinedReadOnly(kalshi, technical);
    }

    public static IntelligenceSnapshot combinedReadOnly(
        KalshiMarketService markets,
        BtcTechnicalService technical
    ) {
        CombinedMarketSnapshot market = markets.combinedSnapshot();
        Map<String, Object> indicators = technical.snapshot();
        return new IntelligenceSnapshot(
            market,
            indicators,
            market.validForSettlementComparison() &&
                Boolean.TRUE.equals(indicators.get("technicalContextReady")),
            "Kraken spot indicators are context only; BRTI determines official Kalshi settlement. " +
                "No calibrated win probability or approved executable quote. NEVER infer positive EV from TA confluence alone."
        );
    }

    public record IntelligenceSnapshot(
        @JsonUnwrapped CombinedMarketSnapshot marketContext,
        Map<String, Object> technical,
        boolean combinedContextReady,
        String technicalWarning
    ) {}
}

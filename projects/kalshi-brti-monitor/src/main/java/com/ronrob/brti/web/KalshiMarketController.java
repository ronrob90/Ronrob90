package com.ronrob.brti.web;

import com.ronrob.brti.feed.KalshiMarketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Browser-accessible read-only local endpoints, never accepts trade instructions. */
@RestController
@RequestMapping("/api/market")
public class KalshiMarketController {
    private final KalshiMarketService markets;

    public KalshiMarketController(KalshiMarketService markets) {
        this.markets = markets;
    }

    @GetMapping("/current")
    public Map<String, Object> current() {
        return markets.marketSnapshot();
    }

    @GetMapping("/combined")
    public Map<String, Object> combined() {
        return markets.combinedSnapshot();
    }
}

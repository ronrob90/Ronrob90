package com.ronrob.brti.web;

import com.ronrob.brti.feed.KalshiMarketService;
import com.ronrob.brti.market.CombinedMarketSnapshot;
import com.ronrob.brti.market.MarketSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Browser-accessible read-only local endpoints, never accepts trade instructions. */
@RestController
@RequestMapping("/api/market")
public class KalshiMarketController {

    private final KalshiMarketService markets;

    public KalshiMarketController(KalshiMarketService markets) {
        this.markets = markets;
    }

    @GetMapping("/current")
    public MarketSnapshot current() {
        return markets.marketSnapshot();
    }

    @GetMapping("/combined")
    public CombinedMarketSnapshot combined() {
        return markets.combinedSnapshot();
    }
}

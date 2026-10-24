package com.ronrob.brti.web;

import com.ronrob.brti.feed.BrtiTracker;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BrtiController {
    private final BrtiTracker tracker;

    public BrtiController(BrtiTracker tracker) {
        this.tracker = tracker;
    }

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        return tracker.snapshot();
    }

    /** Manual price-to-beat input, not trading or order placement. */
    @PostMapping("/target")
    public ResponseEntity<?> updateTarget(@RequestBody TargetRequest request) {
        try {
            tracker.setStrike(request.value());
            return ResponseEntity.ok(Map.of("updated", true));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error.getMessage()));
        }
    }

    public record TargetRequest(BigDecimal value) {}
}

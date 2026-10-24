package com.familytracker.controller;

import com.familytracker.model.Prize;
import com.familytracker.service.PrizeService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prizes")
@CrossOrigin
public class PrizeController {

    private final PrizeService prizeService;

    public PrizeController(PrizeService prizeService) {
        this.prizeService = prizeService;
    }

    @GetMapping
    public ResponseEntity<List<Prize>> getAll() {
        return ResponseEntity.ok(prizeService.getAll());
    }

    @GetMapping("/milestone/{milestoneId}")
    public ResponseEntity<List<Prize>> getByMilestone(@PathVariable Long milestoneId) {
        return ResponseEntity.ok(prizeService.getByMilestoneId(milestoneId));
    }

    @PostMapping
    public ResponseEntity<Prize> create(@RequestBody Prize prize) {
        return ResponseEntity.ok(prizeService.save(prize));
    }
}

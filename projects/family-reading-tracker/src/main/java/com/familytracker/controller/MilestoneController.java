package com.familytracker.controller;

import com.familytracker.model.Milestone;
import com.familytracker.service.MilestoneService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/milestones")
@CrossOrigin
public class MilestoneController {

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping
    public ResponseEntity<List<Milestone>> getAllMilestones() {
        return ResponseEntity.ok(milestoneService.getAll());
    }

    @PostMapping
    public ResponseEntity<Milestone> create(@RequestBody Milestone milestone) {
        return ResponseEntity.ok(milestoneService.save(milestone));
    }
}

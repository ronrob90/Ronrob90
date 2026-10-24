package com.familytracker.controller;

import com.familytracker.model.ReadingSession;
import com.familytracker.service.ReadingSessionService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin
public class ReadingSessionController {

    private final ReadingSessionService sessionService;

    public ReadingSessionController(ReadingSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<ReadingSession> logSession(@RequestBody ReadingSession session) {
        return ResponseEntity.ok(sessionService.save(session));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReadingSession>> getUserSessions(@PathVariable Long userId) {
        return ResponseEntity.ok(sessionService.getByUser(userId));
    }

    @GetMapping
    public ResponseEntity<List<ReadingSession>> getAllSessions() {
        return ResponseEntity.ok(sessionService.getAll());
    }
}

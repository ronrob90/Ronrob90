package com.familytracker.service;

import com.familytracker.model.ReadingSession;
import com.familytracker.Repository.ReadingSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ReadingSessionService {

    private final ReadingSessionRepository sessionRepository;

    public ReadingSessionService(ReadingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    // Save or update a reading session
    public ReadingSession save(ReadingSession session) {
        return sessionRepository.save(session);
    }

    // Get all reading sessions
    public List<ReadingSession> getAll() {
        return sessionRepository.findAll();
    }

    // Get reading sessions by user ID
    public List<ReadingSession> getByUser(Long userId) {
        return sessionRepository.findByUserId(userId);
    }

    // Get a single reading session by ID
    public Optional<ReadingSession> getById(Long id) {
        return sessionRepository.findById(id);
    }

    // Delete a session by ID
    public void deleteById(Long id) {
        sessionRepository.deleteById(id);
    }
}

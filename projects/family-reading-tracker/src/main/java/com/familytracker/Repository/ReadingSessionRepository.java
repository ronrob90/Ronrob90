package com.familytracker.Repository;

import com.familytracker.model.ReadingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {
    List<ReadingSession> findByUserId(Long userId);
}

package com.familytracker.repository;

import com.familytracker.model.ReadingSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {
    List<ReadingSession> findByUserId(Long userId);
}

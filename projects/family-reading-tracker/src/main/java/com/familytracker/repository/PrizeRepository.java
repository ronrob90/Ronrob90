package com.familytracker.repository;

import com.familytracker.model.Prize;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrizeRepository extends JpaRepository<Prize, Long> {
    List<Prize> findByMilestoneId(Long milestoneId);
}

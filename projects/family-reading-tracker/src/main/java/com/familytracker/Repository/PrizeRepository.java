package com.familytracker.Repository;

import com.familytracker.model.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrizeRepository extends JpaRepository<Prize, Long> {
    List<Prize> findByMilestoneId(Long milestoneId);
}


package com.familytracker.Repository;

import com.familytracker.model.Family;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, Long> {
}

package com.familytracker.service;

import com.familytracker.model.Milestone;
import com.familytracker.Repository.MilestoneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;

    public MilestoneService(MilestoneRepository milestoneRepository) {
        this.milestoneRepository = milestoneRepository;
    }

    // Create or update a milestone
    public Milestone save(Milestone milestone) {
        return milestoneRepository.save(milestone);
    }

    // Get all milestones
    public List<Milestone> getAll() {
        return milestoneRepository.findAll();
    }

    // Get a single milestone by ID
    public Optional<Milestone> getById(Long id) {
        return milestoneRepository.findById(id);
    }

    // Delete a milestone by ID
    public void delete(Long id) {
        milestoneRepository.deleteById(id);
    }
}

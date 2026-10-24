package com.familytracker.service;

import com.familytracker.model.Family;
import com.familytracker.Repository.FamilyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FamilyService {

    private final FamilyRepository familyRepository;

    public FamilyService(FamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    public Family save(Family family) {
        return familyRepository.save(family);
    }

    public List<Family> getAll() {
        return familyRepository.findAll();
    }

    public Optional<Family> getById(Long id) {
        return familyRepository.findById(id);
    }

    public void delete(Long id) {
        familyRepository.deleteById(id);
    }
}

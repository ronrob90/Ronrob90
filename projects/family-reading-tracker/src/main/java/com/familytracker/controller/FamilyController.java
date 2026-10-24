package com.familytracker.controller;

import com.familytracker.model.Family;
import com.familytracker.service.FamilyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/families")
@CrossOrigin
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping
    public ResponseEntity<Family> createFamily(@RequestBody Family family) {
        return ResponseEntity.status(HttpStatus.CREATED).body(familyService.save(family));
    }

    @GetMapping
    public ResponseEntity<List<Family>> getAllFamilies() {
        return ResponseEntity.ok(familyService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Family> getFamily(@PathVariable Long id) {
        return ResponseEntity.ok(
                familyService.getById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Family with ID " + id + " not found."
                        ))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFamily(@PathVariable Long id) {
        if (familyService.getById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found.");
        }

        familyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
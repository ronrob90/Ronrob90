package com.familytracker.service;

import com.familytracker.model.Prize;
import com.familytracker.repository.PrizeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PrizeService {

    private final PrizeRepository prizeRepository;

    public PrizeService(PrizeRepository prizeRepository) {
        this.prizeRepository = prizeRepository;
    }

    public Prize save(Prize prize) {
        return prizeRepository.save(prize);
    }

    public List<Prize> getAll() {
        return prizeRepository.findAll();
    }

    public List<Prize> getByMilestoneId(Long milestoneId) {
        return prizeRepository.findByMilestoneId(milestoneId);
    }

    public Optional<Prize> getById(Long id) {
        return prizeRepository.findById(id);
    }

    public void delete(Long id) {
        prizeRepository.deleteById(id);
    }
}

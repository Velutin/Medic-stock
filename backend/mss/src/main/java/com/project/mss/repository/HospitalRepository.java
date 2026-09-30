package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.Hospital;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    List<Hospital> findByActiveTrueOrderByNameAsc();
    Optional<Hospital> findByAcronymIgnoreCase(String acronym);
    boolean existsByNameIgnoreCase(String name);
}

package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.Hospital;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    List<Hospital> findByActiveTrueOrderByNameAsc();
    Optional<Hospital> findByAcronymIgnoreCase(String acronym);
    boolean existsByNameIgnoreCase(String name);

    /** Distribution center that supplies the hospital, if any. */
    @Query("SELECT c FROM Hospital c JOIN c.coveredHospitals h WHERE h.id = :hospitalId")
    Optional<Hospital> findCenterOf(@Param("hospitalId") Long hospitalId);
}

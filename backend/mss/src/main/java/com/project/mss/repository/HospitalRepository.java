package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.Hospital;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    List<Hospital> findByActiveTrueOrderByNameAsc();

    List<Hospital> findAllByOrderByNameAsc();
    Optional<Hospital> findByAcronymIgnoreCase(String acronym);
    boolean existsByNameIgnoreCase(String name);

    /** Distribution center that supplies the hospital, if any. */
    @Query("SELECT c FROM Hospital c JOIN c.coveredHospitals h WHERE h.id = :hospitalId")
    Optional<Hospital> findCenterOf(@Param("hospitalId") Long hospitalId);

    /**
     * Hospitals supplied by a distribution center. Their storeroom is the center's, not their own: material
     * for them enters the center and reaches them through a transfer, so they have no storeroom of their own.
     */
    @Query("SELECT h.id FROM Hospital c JOIN c.coveredHospitals h")
    java.util.Set<Long> findCoveredHospitalIds();
}

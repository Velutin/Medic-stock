package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.HospitalPrice;

public interface HospitalPriceRepository extends JpaRepository<HospitalPrice, Long> {

    Optional<HospitalPrice> findByHospitalIdAndMaterialId(Long hospitalId, Long materialId);

    @Query("SELECT p FROM HospitalPrice p JOIN FETCH p.material m WHERE p.hospital.id = :hospitalId ORDER BY m.ref")
    List<HospitalPrice> listByHospital(@Param("hospitalId") Long hospitalId);
}

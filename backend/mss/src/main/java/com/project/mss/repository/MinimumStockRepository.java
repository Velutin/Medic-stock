package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.MinimumStock;

public interface MinimumStockRepository extends JpaRepository<MinimumStock, Long> {

    Optional<MinimumStock> findByHospitalIdAndMaterialId(Long hospitalId, Long materialId);

    @Query("SELECT e FROM MinimumStock e JOIN FETCH e.material m WHERE e.hospital.id = :hospitalId ORDER BY m.ref")
    List<MinimumStock> listByHospital(@Param("hospitalId") Long hospitalId);
}

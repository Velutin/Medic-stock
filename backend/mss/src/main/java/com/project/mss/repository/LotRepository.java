package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.Lot;

public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByMaterialIdAndNumberIgnoreCase(Long materialId, String number);

    @Query("SELECT l FROM Lot l WHERE UPPER(l.number) = UPPER(:number)")
    List<Lot> findByNumber(@Param("number") String number);
}

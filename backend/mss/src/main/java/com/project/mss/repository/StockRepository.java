package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.Stock;
import com.project.mss.model.enums.Location;

import jakarta.persistence.LockModeType;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /** Locks the balance row to prevent double debits in concurrent operations. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT e FROM Stock e
           WHERE e.lot.id = :lotId AND e.hospital.id = :hospitalId AND e.location = :location
           """)
    Optional<Stock> lock(@Param("lotId") Long lotId,
                             @Param("hospitalId") Long hospitalId,
                             @Param("location") Location location);

    @Query("""
           SELECT e FROM Stock e
           JOIN FETCH e.lot l JOIN FETCH l.material m
           WHERE e.hospital.id = :hospitalId AND e.quantity > 0
           ORDER BY m.ref, l.expiryDate, l.number
           """)
    List<Stock> listByHospital(@Param("hospitalId") Long hospitalId);

    @Query("""
           SELECT e FROM Stock e
           JOIN FETCH e.lot l JOIN FETCH l.material m
           WHERE e.hospital.id = :hospitalId AND e.location = :location
             AND m.id = :materialId AND e.quantity > 0
           ORDER BY l.expiryDate ASC, l.number
           """)
    List<Stock> listByMaterialFefo(@Param("hospitalId") Long hospitalId,
                                        @Param("materialId") Long materialId,
                                        @Param("location") Location location);

    @Query("""
           SELECT e FROM Stock e JOIN FETCH e.hospital
           WHERE e.lot.id = :lotId AND e.quantity > 0
           """)
    List<Stock> listByLot(@Param("lotId") Long lotId);
}

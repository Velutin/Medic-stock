package com.project.mss.repository;

import java.util.Collection;
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
           SELECT e FROM Stock e
           JOIN FETCH e.lot l JOIN FETCH e.hospital h
           WHERE l.material.id IN :materialIds AND e.quantity > 0
           ORDER BY h.name, l.expiryDate, l.number
           """)
    List<Stock> listByMaterials(@Param("materialIds") java.util.Collection<Long> materialIds);

    @Query("""
           SELECT e FROM Stock e JOIN FETCH e.hospital
           WHERE e.lot.id = :lotId AND e.quantity > 0
           """)
    List<Stock> listByLot(@Param("lotId") Long lotId);

    /** Units of valid (not expired) lots in the given hospitals, inside the hospital and in the storeroom. */
    @Query("""
           SELECT COALESCE(SUM(e.quantity), 0) FROM Stock e
           WHERE e.hospital.id IN :hospitalIds AND e.quantity > 0 AND e.lot.expiryDate >= :today
           """)
    long sumValidUnits(@Param("hospitalIds") Collection<Long> hospitalIds, @Param("today") java.time.LocalDate today);

    /** Lots with balance whose expiry date is within [from, to]. */
    @Query("""
           SELECT COUNT(DISTINCT e.lot.id) FROM Stock e
           WHERE e.hospital.id IN :hospitalIds AND e.quantity > 0 AND e.lot.expiryDate BETWEEN :from AND :to
           """)
    long countLotsExpiringBetween(@Param("hospitalIds") Collection<Long> hospitalIds,
                                  @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    /** Lots with balance already expired. */
    @Query("""
           SELECT COUNT(DISTINCT e.lot.id) FROM Stock e
           WHERE e.hospital.id IN :hospitalIds AND e.quantity > 0 AND e.lot.expiryDate < :today
           """)
    long countExpiredLots(@Param("hospitalIds") Collection<Long> hospitalIds, @Param("today") java.time.LocalDate today);

    /**
     * Stock by lot and hospital, for the given hospitals. term (optional, partial, case-insensitive)
     * matches the lot number (also without its leading zeros), REF, name (component), description or GTIN.
     */
    @Query(value = """
           SELECT new com.project.mss.dto.stock.LotStockDTO(
                  l.id, m.id, m.ref, m.component, m.description, m.size, m.color, l.number, l.expiryDate,
                  h.id, h.name,
                  SUM(CASE WHEN e.location = com.project.mss.model.enums.Location.HOSPITAL THEN e.quantity ELSE 0 END),
                  SUM(CASE WHEN e.location = com.project.mss.model.enums.Location.STOREROOM THEN e.quantity ELSE 0 END))
           FROM Stock e JOIN e.lot l JOIN l.material m JOIN e.hospital h
           WHERE e.quantity > 0 AND h.id IN :hospitalIds
             AND (:term IS NULL
                  OR UPPER(l.number) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR TRIM(LEADING '0' FROM UPPER(l.number)) LIKE CONCAT('%', TRIM(LEADING '0' FROM UPPER(CAST(:term AS String))), '%')
                  OR UPPER(m.ref) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.component) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.description) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR m.gtin LIKE CONCAT('%', CAST(:term AS String), '%'))
           GROUP BY l.id, m.id, m.ref, m.component, m.description, m.size, m.color, l.number, l.expiryDate, h.id, h.name
           ORDER BY m.ref, l.expiryDate, l.number, h.name
           """,
           countQuery = """
           SELECT COUNT(DISTINCT CONCAT(CAST(l.id AS String), '-', CAST(h.id AS String)))
           FROM Stock e JOIN e.lot l JOIN l.material m JOIN e.hospital h
           WHERE e.quantity > 0 AND h.id IN :hospitalIds
             AND (:term IS NULL
                  OR UPPER(l.number) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR TRIM(LEADING '0' FROM UPPER(l.number)) LIKE CONCAT('%', TRIM(LEADING '0' FROM UPPER(CAST(:term AS String))), '%')
                  OR UPPER(m.ref) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.component) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.description) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR m.gtin LIKE CONCAT('%', CAST(:term AS String), '%'))
           """)
    org.springframework.data.domain.Page<com.project.mss.dto.stock.LotStockDTO> searchLots(
            @Param("hospitalIds") java.util.Collection<Long> hospitalIds,
            @Param("term") String term,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Stock by lot at one location only, for the given hospitals: the storeroom tab. Same search as
     * searchLots, with the balance of the other location left out - so for STOREROOM the hospital
     * quantity of the DTO comes back as zero, and only lots with balance at that location are listed.
     */
    @Query(value = """
           SELECT new com.project.mss.dto.stock.LotStockDTO(
                  l.id, m.id, m.ref, m.component, m.description, m.size, m.color, l.number, l.expiryDate,
                  h.id, h.name,
                  SUM(CASE WHEN e.location = com.project.mss.model.enums.Location.HOSPITAL THEN e.quantity ELSE 0 END),
                  SUM(CASE WHEN e.location = com.project.mss.model.enums.Location.STOREROOM THEN e.quantity ELSE 0 END))
           FROM Stock e JOIN e.lot l JOIN l.material m JOIN e.hospital h
           WHERE e.quantity > 0 AND e.location = :location AND h.id IN :hospitalIds
             AND (:term IS NULL
                  OR UPPER(l.number) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR TRIM(LEADING '0' FROM UPPER(l.number)) LIKE CONCAT('%', TRIM(LEADING '0' FROM UPPER(CAST(:term AS String))), '%')
                  OR UPPER(m.ref) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.component) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.description) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR m.gtin LIKE CONCAT('%', CAST(:term AS String), '%'))
           GROUP BY l.id, m.id, m.ref, m.component, m.description, m.size, m.color, l.number, l.expiryDate, h.id, h.name
           ORDER BY m.ref, l.expiryDate, l.number, h.name
           """,
           countQuery = """
           SELECT COUNT(DISTINCT CONCAT(CAST(l.id AS String), '-', CAST(h.id AS String)))
           FROM Stock e JOIN e.lot l JOIN l.material m JOIN e.hospital h
           WHERE e.quantity > 0 AND e.location = :location AND h.id IN :hospitalIds
             AND (:term IS NULL
                  OR UPPER(l.number) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR TRIM(LEADING '0' FROM UPPER(l.number)) LIKE CONCAT('%', TRIM(LEADING '0' FROM UPPER(CAST(:term AS String))), '%')
                  OR UPPER(m.ref) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.component) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.description) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR m.gtin LIKE CONCAT('%', CAST(:term AS String), '%'))
           """)
    org.springframework.data.domain.Page<com.project.mss.dto.stock.LotStockDTO> searchLotsAt(
            @Param("hospitalIds") java.util.Collection<Long> hospitalIds,
            @Param("term") String term,
            @Param("location") Location location,
            org.springframework.data.domain.Pageable pageable);
}

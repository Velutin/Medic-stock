package com.project.mss.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.SurgeryItem;

public interface SurgeryItemRepository extends JpaRepository<SurgeryItem, Long> {

    /** Items of a material still without price, in surgeries (not cancelled) of the given hospitals. */
    @Query("""
           SELECT i FROM SurgeryItem i JOIN FETCH i.surgery s
           WHERE i.unitValue IS NULL AND i.lot.material.id = :materialId
             AND s.hospital.id IN :hospitalIds
             AND s.status <> com.project.mss.model.enums.SurgeryStatus.CANCELLED
           """)
    List<SurgeryItem> listWithoutPrice(@Param("hospitalIds") Collection<Long> hospitalIds,
                                       @Param("materialId") Long materialId);

    /** Every item still without price in the given hospitals (not cancelled surgeries), newest first. */
    @Query(value = """
           SELECT i FROM SurgeryItem i JOIN FETCH i.surgery s JOIN FETCH i.lot l JOIN FETCH l.material
           WHERE i.unitValue IS NULL AND s.hospital.id IN :hospitalIds
             AND s.status <> com.project.mss.model.enums.SurgeryStatus.CANCELLED
           ORDER BY s.surgeryDate DESC, i.id
           """,
           countQuery = """
           SELECT COUNT(i) FROM SurgeryItem i JOIN i.surgery s
           WHERE i.unitValue IS NULL AND s.hospital.id IN :hospitalIds
             AND s.status <> com.project.mss.model.enums.SurgeryStatus.CANCELLED
           """)
    Page<SurgeryItem> pageWithoutPrice(@Param("hospitalIds") Collection<Long> hospitalIds, Pageable pageable);

    /** True when the lot was withdrawn in a surgery that was not cancelled. */
    @Query("""
           SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM SurgeryItem i
           WHERE i.lot.id = :lotId AND i.surgery.status <> com.project.mss.model.enums.SurgeryStatus.CANCELLED
           """)
    boolean usedInSurgery(@Param("lotId") Long lotId);
}

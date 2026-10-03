package com.project.mss.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.Surgery;
import com.project.mss.model.enums.SurgeryStatus;

public interface SurgeryRepository extends JpaRepository<Surgery, Long> {

    @Query("""
           SELECT c FROM Surgery c
           WHERE c.hospital.id IN :hospitalIds
             AND c.surgeryDate BETWEEN :start AND :end
           ORDER BY c.surgeryDate DESC, c.id DESC
           """)
    Page<Surgery> list(@Param("hospitalIds") Collection<Long> hospitalIds,
                          @Param("start") LocalDate start,
                          @Param("end") LocalDate end,
                          Pageable pageable);

    /** Surgeries (not cancelled) per hospital by surgery date: rows of {hospitalId, hospitalName, count}. */
    @Query("""
           SELECT h.id, h.name, COUNT(c) FROM Surgery c JOIN c.hospital h
           WHERE h.id IN :hospitalIds AND c.surgeryDate BETWEEN :start AND :end
             AND c.status <> com.project.mss.model.enums.SurgeryStatus.CANCELLED
           GROUP BY h.id, h.name ORDER BY h.name
           """)
    List<Object[]> countByHospital(@Param("hospitalIds") Collection<Long> hospitalIds,
                                   @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Completed surgeries of the given hospitals completed in [start, end). */
    @Query("""
           SELECT c FROM Surgery c JOIN FETCH c.hospital LEFT JOIN FETCH c.surgicalTech
           WHERE c.status = com.project.mss.model.enums.SurgeryStatus.COMPLETED
             AND c.hospital.id IN :hospitalIds
             AND c.completedAt >= :start AND c.completedAt < :end
           ORDER BY c.completedAt, c.id
           """)
    List<Surgery> listCompleted(@Param("hospitalIds") Collection<Long> hospitalIds,
                                @Param("start") java.time.LocalDateTime start,
                                @Param("end") java.time.LocalDateTime end);

    @Query("""
           SELECT c FROM Surgery c LEFT JOIN FETCH c.surgicalTech
           WHERE c.surgeryDate BETWEEN :start AND :end AND c.status IN :status
           """)
    List<Surgery> listByPeriod(@Param("start") LocalDate start,
                                    @Param("end") LocalDate end,
                                    @Param("status") Collection<SurgeryStatus> status);
}

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

    @Query("""
           SELECT c FROM Surgery c LEFT JOIN FETCH c.surgicalTech
           WHERE c.surgeryDate BETWEEN :start AND :end AND c.status IN :status
           """)
    List<Surgery> listByPeriod(@Param("start") LocalDate start,
                                    @Param("end") LocalDate end,
                                    @Param("status") Collection<SurgeryStatus> status);
}

package com.project.mss.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.Material;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    long countBySectionId(Long sectionId);

    Optional<Material> findByRefIgnoreCase(String ref);

    Optional<Material> findByGtin(String gtin);

    @Query("""
           SELECT m FROM Material m
           WHERE (:term IS NULL
                  OR UPPER(m.ref) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%'))
                  OR UPPER(m.description) LIKE UPPER(CONCAT('%', CAST(:term AS String), '%')))
           ORDER BY m.ref
           """)
    Page<Material> find(@Param("term") String term, Pageable pageable);
}

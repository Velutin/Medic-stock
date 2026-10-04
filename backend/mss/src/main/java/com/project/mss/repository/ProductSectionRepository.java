package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.ProductSection;

public interface ProductSectionRepository extends JpaRepository<ProductSection, Long> {

    List<ProductSection> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<ProductSection> findByNameIgnoreCase(String name);
}

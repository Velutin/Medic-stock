package com.project.mss.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.SurgeryItem;

public interface SurgeryItemRepository extends JpaRepository<SurgeryItem, Long> {
}

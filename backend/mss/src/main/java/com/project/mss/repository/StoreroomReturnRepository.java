package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.StoreroomReturn;

public interface StoreroomReturnRepository extends JpaRepository<StoreroomReturn, Long> {
    List<StoreroomReturn> findTop50ByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}

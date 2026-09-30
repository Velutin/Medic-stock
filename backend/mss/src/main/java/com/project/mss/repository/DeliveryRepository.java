package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.Delivery;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    List<Delivery> findTop50ByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}

package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.SupplierOrder;

public interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    List<SupplierOrder> findTop50ByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}

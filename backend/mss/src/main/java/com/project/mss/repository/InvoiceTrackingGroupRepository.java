package com.project.mss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.InvoiceTrackingGroup;

public interface InvoiceTrackingGroupRepository extends JpaRepository<InvoiceTrackingGroup, Long> {

    boolean existsByTrackingCodeAndInvoiceId(String trackingCode, Long invoiceId);

    List<InvoiceTrackingGroup> findByTrackingCode(String trackingCode);
}

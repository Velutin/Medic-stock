package com.project.mss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.InvoiceSupplier;

public interface InvoiceSupplierRepository extends JpaRepository<InvoiceSupplier, Long> {

    Optional<InvoiceSupplier> findByCnpj(String cnpj);

    List<InvoiceSupplier> findByActiveTrueOrderByNameAsc();
}

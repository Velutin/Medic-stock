package com.project.mss.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.SupplierInvoiceItemLot;
import com.project.mss.model.enums.InvoiceStatus;

public interface SupplierInvoiceItemLotRepository extends JpaRepository<SupplierInvoiceItemLot, Long> {

    /**
     * Lots declared by invoices that are still owed, matched by the comparable lot key.
     *
     * The key is the lot number without leading zeros, so a label printed as 005706061 finds the 5706061 of
     * the invoice. Only invoices still waiting for material are looked at: what a received invoice declared
     * is history, not an announcement.
     */
    @Query("""
            SELECT l FROM SupplierInvoiceItemLot l
            WHERE l.lotKey = :lotKey
              AND l.invoiceItem.invoice.status IN :statuses
              AND l.invoiceItem.receivedQuantity < l.invoiceItem.quantity
            ORDER BY COALESCE(l.invoiceItem.invoice.issuedAt, l.invoiceItem.invoice.createdAt) ASC, l.id ASC
            """)
    List<SupplierInvoiceItemLot> owedByKey(@Param("lotKey") String lotKey,
                                           @Param("statuses") Collection<InvoiceStatus> statuses);
}

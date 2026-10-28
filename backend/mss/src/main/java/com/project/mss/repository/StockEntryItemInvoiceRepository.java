package com.project.mss.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.StockEntryItemInvoice;

public interface StockEntryItemInvoiceRepository extends JpaRepository<StockEntryItemInvoice, Long> {

    /** Links of one entry, for the screen and for undoing them when the entry is corrected. */
    @Query("""
            SELECT l FROM StockEntryItemInvoice l
            WHERE l.stockEntryItem.stockEntry.id = :entryId
            ORDER BY l.id ASC
            """)
    List<StockEntryItemInvoice> ofEntry(@Param("entryId") Long entryId);

    /** Invoices an entry touched, to show which ones it settled. */
    @Query("""
            SELECT DISTINCT l.invoiceItem.invoice.id FROM StockEntryItemInvoice l
            WHERE l.stockEntryItem.stockEntry.id = :entryId
            """)
    List<Long> invoiceIdsOfEntry(@Param("entryId") Long entryId);

    /**
     * Lots an invoice brought in.
     *
     * This is what "movements of invoice 6886" means in practice: the system tracks lots, not individual
     * units, so a lot that also arrived in another invoice carries the movements of both. The screen says
     * as much - claiming otherwise would be a precision the data does not have.
     */
    @Query("""
            SELECT DISTINCT l.stockEntryItem.lot.id FROM StockEntryItemInvoice l
            WHERE l.invoiceItem.invoice.id IN :invoiceIds AND l.stockEntryItem IS NOT NULL
            """)
    List<Long> lotIdsOfInvoices(@Param("invoiceIds") Collection<Long> invoiceIds);

    /**
     * Invoice numbers of each lot, to name the origin invoice on the stock spreadsheets.
     *
     * A lot can carry more than one number: the same lot may have arrived in two invoices, and the
     * spreadsheet prints both rather than picking one and calling it the origin.
     */
    @Query("""
            SELECT DISTINCT l.stockEntryItem.lot.id, l.invoiceItem.invoice.number
            FROM StockEntryItemInvoice l
            WHERE l.stockEntryItem.lot.id IN :lotIds
            ORDER BY l.invoiceItem.invoice.number ASC
            """)
    List<Object[]> invoiceNumbersOfLots(@Param("lotIds") Collection<Long> lotIds);

    /** Invoice of each entry, to name the invoice on the entry rows of the movements report. */
    @Query("""
            SELECT l.stockEntryItem.stockEntry.id, l.invoiceItem.invoice.number, l.invoiceItem.invoice.emitterName
            FROM StockEntryItemInvoice l
            WHERE l.stockEntryItem.stockEntry.id IN :entryIds
            """)
    List<Object[]> invoicesOfEntries(@Param("entryIds") Collection<Long> entryIds);
}

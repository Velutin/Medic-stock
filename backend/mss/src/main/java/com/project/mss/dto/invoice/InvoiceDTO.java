package com.project.mss.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.SupplierInvoice;
import com.project.mss.model.entity.SupplierInvoiceItem;
import com.project.mss.model.enums.InvoiceItemCategory;
import com.project.mss.model.enums.InvoiceStatus;

/**
 * An invoice as the screen shows it.
 *
 * daysOpen and overdue travel with the invoice so the list does not have to recompute the rule: the number of
 * days that makes an invoice overdue is a setting, and only the server knows its current value.
 */
public record InvoiceDTO(Long id, String accessKey, String emitterName, String emitterCnpj, String number,
                         String series, LocalDateTime issuedAt, String operation, String cfop,
                         String recipientName, String carrierName, String trackingCode, LocalDate surgeryDate,
                         BigDecimal totalValue, InvoiceStatus status, LocalDate receivedAt, String closeReason,
                         long daysOpen, boolean overdue, int announcedUnits, int receivedUnits,
                         int implantLines, int instrumentLines, boolean hasPdf, boolean pdfExpired,
                         List<Line> items) {

    /**
     * One line of the invoice. lots are the ones the XML declared: with a single lot the receipt is certain,
     * with several only the label scanned at the entry says which came, which is why they travel as a list
     * and not as a division of the quantity.
     */
    public record Line(Long id, int itemNumber, String productCode, String gtin, String description,
                       Long materialId, String ref, String materialDescription, String color,
                       InvoiceItemCategory category, int quantity, int receivedQuantity, int missing,
                       List<Lot> lots) { }

    public record Lot(String number, LocalDate expiryDate, Integer quantity) { }

    public static InvoiceDTO of(SupplierInvoice invoice, int alertDays) {
        List<Line> lines = invoice.getItems().stream().map(InvoiceDTO::line).toList();
        long daysOpen = invoice.daysOpen(LocalDate.now());
        boolean overdue = invoice.getStatus().isOpen() && daysOpen > alertDays;
        return new InvoiceDTO(invoice.getId(), invoice.getAccessKey(), invoice.getEmitterName(),
                invoice.getEmitterCnpj(), invoice.getNumber(), invoice.getSeries(), invoice.getIssuedAt(),
                invoice.getOperation(), invoice.getCfop(), invoice.getRecipientName(), invoice.getCarrierName(),
                invoice.getTrackingCode(), invoice.getSurgeryDate(), invoice.getTotalValue(), invoice.getStatus(),
                invoice.getReceivedAt(), invoice.getCloseReason(), daysOpen, overdue,
                invoice.announcedUnits(), invoice.receivedUnits(),
                (int) lines.stream().filter(l -> l.category() == InvoiceItemCategory.IMPLANT).count(),
                (int) lines.stream().filter(l -> l.category() == InvoiceItemCategory.INSTRUMENT).count(),
                invoice.getPdfFile() != null, invoice.getPdfExpiredAt() != null,
                lines);
    }

    private static Line line(SupplierInvoiceItem item) {
        Material material = item.getMaterial();
        List<Lot> lots = item.getLots().stream()
                .map(l -> new Lot(l.getLotNumber(), l.getExpiryDate(), l.getQuantity()))
                .toList();
        return new Line(item.getId(), item.getItemNumber(), item.getProductCode(), item.getGtin(),
                item.getDescription(), material == null ? null : material.getId(),
                material == null ? null : material.getRef(),
                material == null ? null : material.getDescription(),
                material == null ? null : material.getColor(),
                item.getCategory(), item.getQuantity(), item.getReceivedQuantity(), item.missing(), lots);
    }
}

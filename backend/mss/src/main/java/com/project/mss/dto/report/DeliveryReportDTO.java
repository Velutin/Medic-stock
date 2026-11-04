package com.project.mss.dto.report;

import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.enums.ShipmentStatus;

/**
 * Delivery document already generated, to download the PDF again: transfers from the storeroom and loans
 * (a loan is delivered to the hospital as a regular delivery, numbered "E-{id}").
 * loan: true for loans (sourceHospital is then the hospital the material came from); pdfPath: where the PDF is.
 * matchedLots: with a lot search, the items of the document with that lot ("REF · lot · quantity"); empty otherwise.
 *
 * units is always what was dispatched - that is what the report counts and what the printed copy that
 * travelled with the box says. What became of those units is told apart, and only once the arrival has been
 * confirmed: arrived is null while the shipment is still on the road, because nothing has happened to it
 * yet. When it is not null and differs from units, returned + lost + pending say where the rest went. A
 * cancelled shipment is not listed at all: nothing left the storeroom.
 *
 * shortfalls names the lots that did not arrive whole, one entry each. A total on its own would be useless
 * on a shipment of twenty lots - "two units missing" would send the reader back to compare the box line by
 * line, which is the work the report exists to spare. It comes back empty for a shipment written off whole:
 * there is nothing to compare when nothing arrived, and listing fifty lots to say so would bury the report.
 */
public record DeliveryReportDTO(Long id, String number, boolean loan, String pdfPath, LocalDateTime createdAt,
                                String hospital, String sourceHospital, int lots, int units, String createdBy,
                                List<String> matchedLots,
                                ShipmentStatus status, Integer arrived, int returned, int lost, int pending,
                                List<Shortfall> shortfalls) {

    /** One lot of the shipment that did not arrive whole: what was sent, what arrived and where the rest went. */
    public record Shortfall(String ref, String lot, int sent, int arrived, int returned, int lost, int pending) { }
}

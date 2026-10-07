package com.project.mss.dto.report;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Delivery document already generated, to download the PDF again: transfers from the storeroom and loans
 * (a loan is delivered to the hospital as a regular delivery, numbered "E-{id}").
 * loan: true for loans (sourceHospital is then the hospital the material came from); pdfPath: where the PDF is.
 * matchedLots: with a lot search, the items of the document with that lot ("REF · lot · quantity"); empty otherwise.
 */
public record DeliveryReportDTO(Long id, String number, boolean loan, String pdfPath, LocalDateTime createdAt,
                                String hospital, String sourceHospital, int lots, int units, String createdBy,
                                List<String> matchedLots) { }

package com.project.mss.dto.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Billing of completed surgeries in the selected months (by completion date), using each hospital's
 * table values recorded at withdrawal. commission = total x commission rate; share = commission x share rate,
 * with the rates in effect in the month each surgery was completed.
 * itemsWithoutValue: items recorded while the REF had no value (not included in the totals until registered).
 */
public record BillingDTO(
        List<YearMonth> months,
        Long hospitalId,
        int surgeryCount,
        int itemCount,
        int itemsWithoutValue,
        BigDecimal totalValue,
        BigDecimal commission,
        BigDecimal share,
        BigDecimal averagePerSurgery,
        List<HospitalBilling> byHospital,
        List<SurgeryBilling> surgeries
) {
    public record HospitalBilling(Long hospitalId, String hospital, String priceTableType, int surgeryCount,
                                  int itemCount, int itemsWithoutValue, BigDecimal totalValue,
                                  BigDecimal commission, BigDecimal share) { }

    public record SurgeryBilling(Long surgeryId, LocalDate surgeryDate, LocalDateTime completedAt, String patientName,
                                 Long hospitalId, String hospital, String surgicalTech, int itemCount,
                                 int itemsWithoutValue, BigDecimal totalValue, BigDecimal commissionRate,
                                 BigDecimal commission, BigDecimal shareRate, BigDecimal share) { }
}

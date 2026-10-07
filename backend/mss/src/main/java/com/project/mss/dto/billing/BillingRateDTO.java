package com.project.mss.dto.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import com.project.mss.model.entity.BillingRate;

/** startMonth: first month the rates apply (they stay valid until the next registered month). */
public record BillingRateDTO(YearMonth startMonth, BigDecimal commissionRate, BigDecimal shareRate,
                             String createdBy, LocalDateTime createdAt) {
    public static BillingRateDTO of(BillingRate r) {
        return new BillingRateDTO(YearMonth.from(r.getValidFrom()), r.getCommissionRate(), r.getShareRate(),
                r.getCreatedBy() != null ? r.getCreatedBy().getName() : null, r.getCreatedAt());
    }
}

package com.project.mss.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Billing rates valid from the first day of a month until the next rate:
 *  - commissionRate: commission over the surgery total (e.g. 20%);
 *  - shareRate: the owner's share over the commission (e.g. 8.5% of the 20%).
 */
@Entity
@Table(name = "billing_rate")
@Getter
@Setter
@NoArgsConstructor
public class BillingRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** First day of the month the rate starts to apply. */
    @Column(name = "valid_from", nullable = false, unique = true)
    private LocalDate validFrom;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "share_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal shareRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

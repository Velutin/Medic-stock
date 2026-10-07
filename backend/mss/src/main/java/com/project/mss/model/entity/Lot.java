package com.project.mss.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lot")
@Getter
@Setter
@NoArgsConstructor
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "material_id")
    private Material material;

    @Column(nullable = false, length = 80)
    private String number;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (number != null) number = number.trim().toUpperCase();
    }

    /**
     * Lot number used to compare lots: upper case, without spaces and without leading zeros. The labels print the lot
     * with 9 digits (e.g. 005706061) and the same lot may be registered without the zeros (5706061): both are one lot.
     */
    public static String comparableNumber(String number) {
        if (number == null) return "";
        String n = number.trim().toUpperCase().replaceFirst("^0+", "");
        return n.isEmpty() ? "0" : n;
    }

    /** True when the number refers to this lot, ignoring case and leading zeros. */
    public boolean hasNumber(String other) {
        return comparableNumber(number).equals(comparableNumber(other));
    }

    /** Expired lots do not count as available stock. */
    public boolean isExpired(LocalDate reference) {
        return expiryDate.isBefore(reference);
    }
}

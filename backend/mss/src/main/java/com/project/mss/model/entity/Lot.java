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

    /**
     * The number as it goes on paper. The same physical lot reaches the system written two ways - the label
     * prints nine characters with the leading zeros (005724085) and a reading or a typed entry may drop
     * them (5724085) - so a delivery note ended up listing both shapes, one line below the other.
     *
     * Printing without the zeros, not with them: the system already treats the two as the same lot (see
     * comparableNumber), so the zeros carry nothing. Padding to nine would mean inventing characters that
     * may not be on the label - a lot that is genuinely shorter would come out with zeros nobody printed -
     * and it would break on the day the supplier lengthens the number.
     *
     * Only for printing. What is stored stays exactly as it was read, which is what matching depends on.
     */
    public static String printedNumber(String number) {
        if (number == null) return "";
        String n = number.trim();
        String stripped = n.replaceFirst("^0+", "");
        return stripped.isEmpty() ? n : stripped; // a number that is all zeros stays as it is
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

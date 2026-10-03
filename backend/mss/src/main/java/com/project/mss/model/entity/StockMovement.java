package com.project.mss.model.entity;

import java.time.LocalDateTime;

import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.MovementType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Immutable history of every balance change. */
@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(nullable = false)
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_hospital_id")
    private Hospital sourceHospital;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_location", length = 10)
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_hospital_id")
    private Hospital destinationHospital;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_location", length = 10)
    private Location destinationLocation;

    @Column(name = "surgery_id")
    private Long surgeryId;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "delivery_id")
    private Long deliveryId;

    /** Stock entry that created (or corrected) this movement. */
    @Column(name = "stock_entry_id")
    private Long stockEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        // A stock entry informs the receipt date; every other movement happens now
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

package com.project.mss.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.LoanType;
import com.project.mss.model.enums.ShipmentStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loan")
@Getter
@Setter
@NoArgsConstructor
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "source_hospital_id")
    private Hospital sourceHospital;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_location", nullable = false, length = 10)
    private Location sourceLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LoanType type = LoanType.LOAN;

    /** Destination of a loan; null for a return to the supplier. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "destination_hospital_id")
    private Hospital destinationHospital;

    /** Why the material was returned to the supplier (returns only). */
    @Column(name = "return_reason", columnDefinition = "TEXT")
    private String returnReason;

    /** Where the shipment is. See {@link ShipmentStatus}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ShipmentStatus status = ShipmentStatus.DISPATCHED;

    /**
     * The day the material reached the destination, as informed by whoever confirmed it - not the day the
     * confirmation was typed. They are often different: the box arrives on Thursday and the arrival is
     * registered on Saturday, from the photo of the signed note.
     */
    @Column(name = "arrived_on")
    private LocalDate arrivedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrived_by")
    private User arrivedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LoanItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

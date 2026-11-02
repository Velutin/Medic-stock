package com.project.mss.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Material taken out of a hospital and back into the storeroom: the opposite way of a {@link Delivery}.
 *
 * It is its own document instead of a Delivery with a direction, because the delivery report is signed by
 * whoever receives the material at the hospital and here the hospital is the one handing it over.
 */
@Entity
@Table(name = "storeroom_return")
@Getter
@Setter
@NoArgsConstructor
public class StoreroomReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hospital the material is taken out of. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    /**
     * Storeroom owner the material goes back to: the hospital itself or, for a hospital supplied by a
     * distribution center, the center (a covered hospital has no storeroom of its own).
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "storeroom_hospital_id")
    private Hospital storeroomHospital;

    /** Why the material is leaving the hospital; required, and printed on the report. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "storeroomReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StoreroomReturnItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

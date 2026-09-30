package com.project.mss.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.LoanStatus;

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

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "destination_hospital_id")
    private Hospital destinationHospital;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoanStatus status = LoanStatus.PENDING_NOTIFICATION;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

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

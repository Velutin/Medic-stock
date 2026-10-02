package com.project.mss.model.entity;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.project.mss.model.enums.HospitalType;
import com.project.mss.model.enums.ProductLine;
import com.project.mss.model.enums.PriceTableType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "hospital")
@Getter
@Setter
@NoArgsConstructor
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(unique = true, length = 30)
    private String acronym;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HospitalType type = HospitalType.HOSPITAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_table_type", length = 20)
    private PriceTableType priceTableType;

    @Column(nullable = false)
    private Boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hospital_product_line", joinColumns = @JoinColumn(name = "hospital_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "product_line", length = 20)
    private Set<ProductLine> productLines = EnumSet.noneOf(ProductLine.class);

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Hospitals supplied by this distribution center (empty for regular hospitals). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "distribution_center_hospital",
               joinColumns = @JoinColumn(name = "center_id"),
               inverseJoinColumns = @JoinColumn(name = "hospital_id"))
    private Set<Hospital> coveredHospitals = new HashSet<>();
    /** Inverse side: the distribution center that supplies this hospital (at most one). Read-only. */
    @ManyToMany(mappedBy = "coveredHospitals", fetch = FetchType.LAZY)
    private Set<Hospital> distributionCenters = new HashSet<>();

    public boolean isDistributionCenter() {
        return type == HospitalType.DISTRIBUTION_CENTER;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

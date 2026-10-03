package com.project.mss.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "material")
@Getter
@Setter
@NoArgsConstructor
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String ref;

    @Column(nullable = false)
    private String description;

    /**
     * GTIN read from AI (01) of the GS1 barcode/QR code. It is the same on every unit
     * of this material, so it identifies the REF; the lot comes from AI (10).
     * Always stored with 14 digits (GTIN-8/12/13 are left-padded with zeros).
     */
    @Column(unique = true, length = 14)
    private String gtin;

    @Column(length = 100)
    private String component;

    /** Product lines the material is used in (at least one; e.g. bone cement: hip, knee and shoulder). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "material_product_line", joinColumns = @JoinColumn(name = "material_id"))
    @Column(name = "product_line", length = 20)
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.BatchSize(size = 100)
    private java.util.Set<com.project.mss.model.enums.ProductLine> productLines = new java.util.HashSet<>();

    @Column(length = 30)
    private String size;

    /** Size identification color, in hex (e.g. #FFD700). */
    @Column(length = 20)
    private String color;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (ref != null) ref = ref.trim().toUpperCase();
    }

    @PreUpdate
    protected void onUpdate() {
        if (ref != null) ref = ref.trim().toUpperCase();
    }
}

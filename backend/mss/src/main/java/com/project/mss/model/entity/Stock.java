package com.project.mss.model.entity;

import java.time.LocalDateTime;

import com.project.mss.model.enums.Location;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Balance of a lot at a hospital. With location = STOREROOM the material is in the storeroom,
 * assigned to that hospital; with HOSPITAL it is already inside the hospital.
 */
@Entity
@Table(name = "stock",
       uniqueConstraints = @UniqueConstraint(columnNames = {"lote_id", "hospital_id", "localizacao"}))
@Getter
@Setter
@NoArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Location location;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Stock(Lot lot, Hospital hospital, Location location) {
        this.lot = lot;
        this.hospital = hospital;
        this.location = location;
        this.quantity = 0;
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}

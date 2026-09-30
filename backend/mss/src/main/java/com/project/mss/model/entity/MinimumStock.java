package com.project.mss.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * hospitalIdeal: minimum inside the hospital (below it, replenish from the storeroom).
 * idealTotal: minimum across hospital + storeroom (below it, order from the supplier).
 */
@Entity
@Table(name = "minimum_stock",
       uniqueConstraints = @UniqueConstraint(columnNames = {"hospital_id", "material_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MinimumStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "material_id")
    private Material material;

    @Column(name = "hospital_ideal", nullable = false)
    private Integer hospitalIdeal = 0;

    @Column(name = "ideal_total", nullable = false)
    private Integer idealTotal = 0;
}

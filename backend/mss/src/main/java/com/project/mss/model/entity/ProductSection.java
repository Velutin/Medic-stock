package com.project.mss.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Section of the catalog (e.g. "Quadril não cimentada", "Bipolar"), registered by the user.
 * The hospital stock by material is grouped by section, in displayOrder.
 */
@Entity
@Table(name = "product_section")
@Getter
@Setter
@NoArgsConstructor
public class ProductSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean active = true;
}

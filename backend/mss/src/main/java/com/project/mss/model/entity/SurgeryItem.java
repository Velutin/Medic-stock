package com.project.mss.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.project.mss.model.enums.ReadSource;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "surgery_item")
@Getter
@Setter
@NoArgsConstructor
public class SurgeryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "surgery_id")
    private Surgery surgery;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(nullable = false)
    private Integer quantity;

    /** Hospital price table value at withdrawal time (SIGTAP or tender). */
    @Column(name = "unit_value", precision = 12, scale = 2)
    private BigDecimal unitValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_source", nullable = false, length = 20)
    private ReadSource readSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

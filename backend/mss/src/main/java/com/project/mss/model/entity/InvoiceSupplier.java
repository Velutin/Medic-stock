package com.project.mss.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A supplier whose invoices are read from the mailbox, identified by CNPJ.
 *
 * The CNPJ is the criterion and the name is only a label: a company changes its trade name and the wording of
 * its operations, the registration number does not. An emitter absent from this table has its invoice filed
 * for review instead of guessed.
 */
@Entity
@Table(name = "invoice_supplier")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (cnpj != null) cnpj = digitsOf(cnpj);
    }

    /** CNPJ without punctuation, the way it travels in the XML. */
    public static String digitsOf(String cnpj) {
        return cnpj == null ? null : cnpj.replaceAll("\\D", "");
    }
}

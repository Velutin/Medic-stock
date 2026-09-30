package com.project.mss.model.entity;

import java.time.LocalDateTime;

import com.project.mss.model.enums.PendingIssueReason;
import com.project.mss.model.enums.ReadSource;
import com.project.mss.model.enums.PendingIssueStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pending_issue")
@Getter
@Setter
@NoArgsConstructor
public class PendingIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "surgery_id")
    private Surgery surgery;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @Column(name = "entered_code", nullable = false, length = 200)
    private String enteredCode;

    @Column(name = "entered_ref", length = 60)
    private String enteredRef;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_source", length = 20)
    private ReadSource readSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PendingIssueReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PendingIssueStatus status = PendingIssueStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

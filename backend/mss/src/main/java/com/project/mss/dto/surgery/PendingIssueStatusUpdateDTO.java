package com.project.mss.dto.surgery;

import com.project.mss.model.enums.PendingIssueStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Status transition of a pending issue.
 * RESOLVED: lotId is required; that lot is recorded in the surgery.
 * DISCARDED: lotId must be empty; the issue is closed with the justification.
 */
public record PendingIssueStatusUpdateDTO(
        @NotNull PendingIssueStatus status,
        Long lotId,
        @NotBlank(message = "Resolution is required") String resolution
) { }
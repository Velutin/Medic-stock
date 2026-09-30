package com.project.mss.dto.surgery;

import jakarta.validation.constraints.NotBlank;

/**
 * lotId provided: the issue is resolved by recording that lot in the surgery.
 * lotId empty: the issue is discarded with the given justification.
 */
public record ResolvePendingIssueDTO(Long lotId, @NotBlank(message = "Resolution is required") String resolution) { }

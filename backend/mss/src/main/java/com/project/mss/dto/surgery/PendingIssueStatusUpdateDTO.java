package com.project.mss.dto.surgery;

import com.project.mss.model.enums.PendingIssueStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Status transition of a pending issue.
 *
 * Surgery issues: RESOLVED needs lotId, and that lot is recorded in the surgery; DISCARDED closes it with
 * the justification and touches no stock.
 *
 * WITHOUT_INVOICE issues (a lot that entered with no invoice behind it) read differently: RESOLVED carries
 * invoiceNumber and writes the lot against that invoice, DISCARDED is "it really came without an invoice",
 * kept with the reason. The stock is not touched either way - the material entered when it was received.
 */
public record PendingIssueStatusUpdateDTO(
        @NotNull PendingIssueStatus status,
        Long lotId,
        String invoiceNumber,
        @NotBlank(message = "Resolution is required") String resolution
) { }
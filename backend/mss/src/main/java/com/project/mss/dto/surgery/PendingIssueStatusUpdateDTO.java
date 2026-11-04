package com.project.mss.dto.surgery;

import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.model.enums.ShipmentOutcome;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Status transition of a pending issue.
 *
 * Surgery issues: RESOLVED needs lotId, and that lot is recorded in the surgery; DISCARDED closes it with
 * the justification and touches no stock.
 *
 * NOT_DELIVERED issues (units that were not in the box) need outcome, and only RESOLVED: discarding one
 * would close the question while its balance stayed on the road for good, counting as stock nobody has.
 *
 * WITHOUT_INVOICE issues (a lot that entered with no invoice behind it) read differently: RESOLVED carries
 * invoiceNumber and writes the lot against that invoice, DISCARDED is "it really came without an invoice",
 * kept with the reason. The stock is not touched either way - the material entered when it was received.
 */
public record PendingIssueStatusUpdateDTO(
        @NotNull PendingIssueStatus status,
        Long lotId,
        String invoiceNumber,
        ShipmentOutcome outcome,
        @NotBlank(message = "Resolution is required") String resolution
) { }
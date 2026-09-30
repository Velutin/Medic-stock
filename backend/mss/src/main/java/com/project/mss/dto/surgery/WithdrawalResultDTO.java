package com.project.mss.dto.surgery;

/** Scan result: either an item or a pending issue was created. warning carries alerts (e.g. item missing from the price table). */
public record WithdrawalResultDTO(boolean recorded, SurgeryItemDTO item, PendingIssueDTO pendingIssue, String warning) { }

package com.project.mss.dto.loan;

import com.project.mss.model.enums.LoanStatus;

import jakarta.validation.constraints.NotNull;

/** Status transition of a loan. The only allowed transition is PENDING_NOTIFICATION -> NOTIFIED. */
public record LoanStatusUpdateDTO(@NotNull LoanStatus status) { }
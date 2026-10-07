package com.project.mss.dto.surgery;

import com.project.mss.model.enums.SurgeryStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Status transition of a surgery. cancellationReason is required when status is CANCELLED. */
public record SurgeryStatusUpdateDTO(
        @NotNull SurgeryStatus status,
        @Size(max = 500) String cancellationReason
) { }
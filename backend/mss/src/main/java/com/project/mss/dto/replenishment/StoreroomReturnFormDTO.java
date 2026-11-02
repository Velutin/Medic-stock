package com.project.mss.dto.replenishment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Takes lots out of a hospital and back into the storeroom, creating a withdrawal document (with a PDF).
 * The storeroom is not informed: it is the hospital's own or, for a hospital supplied by a distribution
 * center, the center's, which the system resolves. Expired lots are allowed here.
 */
public record StoreroomReturnFormDTO(
        @NotNull Long hospitalId,
        @NotBlank(message = "Withdrawal reason is required") @Size(max = 1000) String reason,
        @NotEmpty @Valid List<StoreroomReturnRequestItem> items,
        String notes
) {
    public record StoreroomReturnRequestItem(@NotNull Long lotId, @NotNull @Positive Integer quantity) { }
}

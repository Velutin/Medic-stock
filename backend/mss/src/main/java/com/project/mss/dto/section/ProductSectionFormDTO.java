package com.project.mss.dto.section;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductSectionFormDTO(
        @NotBlank(message = "Section name is required") @Size(max = 80) String name,
        @NotNull(message = "Display order is required") @PositiveOrZero Integer displayOrder,
        Boolean active
) { }

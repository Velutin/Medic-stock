package com.project.mss.dto.material;

import java.util.Set;

import com.project.mss.model.enums.ProductLine;

import jakarta.validation.constraints.NotEmpty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MaterialFormDTO(
        @NotBlank(message = "REF is required") @Size(max = 60) String ref,
        @NotBlank(message = "Description is required") String description,
        @Pattern(regexp = "^$|^\\d{8}$|^\\d{12,14}$", message = "GTIN must have 8, 12, 13 or 14 digits") String gtin,
        @NotEmpty(message = "At least one product line is required") Set<ProductLine> productLines,
        @Size(max = 100) String component,
        @Size(max = 30) String size,
        @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "Color must use the #RRGGBB format") String color,
        Boolean active,
        /** Catalog section (optional). */
        Long sectionId
) { }

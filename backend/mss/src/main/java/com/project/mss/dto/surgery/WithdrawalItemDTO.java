package com.project.mss.dto.surgery;

import com.project.mss.model.enums.ReadSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Item scanned at withdrawal: QR code or GS1 barcode content (GTIN + lot) or typed lot number.
 * ref is optional and only needed when the GTIN is unknown and several materials share the lot number.
 */
public record WithdrawalItemDTO(
        @NotBlank(message = "Scanned code or lot number is required") String code,
        String ref,
        @NotNull ReadSource readSource,
        @Positive Integer quantity
) {
    public int quantityOrOne() {
        return quantity == null ? 1 : quantity;
    }
}

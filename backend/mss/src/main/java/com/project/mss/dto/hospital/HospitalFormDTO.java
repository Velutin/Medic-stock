package com.project.mss.dto.hospital;

import java.util.Set;

import com.project.mss.model.enums.ProductLine;
import com.project.mss.model.enums.PriceTableType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HospitalFormDTO(
        @NotBlank(message = "Name is required") @Size(max = 150) String name,
        @Size(max = 30) String acronym,
        PriceTableType priceTableType,
        Boolean active,
        Set<ProductLine> productLines
) { }

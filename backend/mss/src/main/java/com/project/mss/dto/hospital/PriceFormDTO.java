package com.project.mss.dto.hospital;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PriceFormDTO(@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal value) { }

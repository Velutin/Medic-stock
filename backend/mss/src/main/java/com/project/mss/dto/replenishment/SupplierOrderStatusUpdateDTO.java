package com.project.mss.dto.replenishment;

import com.project.mss.model.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;

/** Status transition of a supplier order. The only allowed transition is GENERATED -> SENT. */
public record SupplierOrderStatusUpdateDTO(@NotNull OrderStatus status) { }
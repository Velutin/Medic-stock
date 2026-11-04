package com.project.mss.dto.replenishment;

import java.time.LocalDate;
import java.util.List;

import com.project.mss.model.enums.ShipmentStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * State transition of a shipment, the same shape the surgery uses: one endpoint, the target state in the
 * body, and the fields that transition needs.
 *
 * ARRIVED: the box reached the hospital. arrivedOn is the day it actually got there, which is often not the
 * day this is typed. Items may be left out entirely - a line nobody mentions arrived whole, which is the
 * ordinary case - so only what came short has to be listed.
 *
 * CANCELLED: it never left. Allowed only while nothing has been confirmed.
 */
public record ShipmentStatusUpdateDTO(
        @NotNull ShipmentStatus status,
        LocalDate arrivedOn,
        @Valid List<ArrivedItem> items
) {
    public record ArrivedItem(@NotNull Long itemId, @NotNull @PositiveOrZero Integer quantity) { }
}

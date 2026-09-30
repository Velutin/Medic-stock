package com.project.mss.dto.replenishment;

import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.SupplierOrder;
import com.project.mss.model.enums.OrderStatus;

public record SupplierOrderDTO(Long id, Long hospitalId, String hospital, OrderStatus status, String notes,
                               LocalDateTime createdAt, LocalDateTime sentAt, List<SupplierOrderLine> items) {

    public record SupplierOrderLine(String ref, String description, int quantity, boolean urgent) { }

    public static SupplierOrderDTO of(SupplierOrder p) {
        return new SupplierOrderDTO(p.getId(), p.getHospital().getId(), p.getHospital().getName(), p.getStatus(),
                p.getNotes(), p.getCreatedAt(), p.getSentAt(),
                p.getItems().stream().map(i -> new SupplierOrderLine(i.getMaterial().getRef(), i.getMaterial().getDescription(),
                        i.getQuantity(), Boolean.TRUE.equals(i.getUrgent()))).toList());
    }
}

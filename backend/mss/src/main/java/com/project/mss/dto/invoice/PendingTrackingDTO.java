package com.project.mss.dto.invoice;

import java.time.LocalDateTime;

import com.project.mss.model.entity.PendingTracking;

/**
 * A tracking code waiting for an invoice that has not been read yet.
 *
 * It is listed on the screen so that a code waiting forever is visible: that means an invoice was announced
 * by the carrier e-mail and its XML never arrived, which is itself something to ask the supplier about.
 */
public record PendingTrackingDTO(Long id, String number, String trackingCode, LocalDateTime createdAt) {

    public static PendingTrackingDTO of(PendingTracking pending) {
        return new PendingTrackingDTO(pending.getId(), pending.getNumber(), pending.getTrackingCode(),
                pending.getCreatedAt());
    }
}

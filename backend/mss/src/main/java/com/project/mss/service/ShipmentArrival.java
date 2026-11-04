package com.project.mss.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.project.mss.dto.replenishment.ShipmentStatusUpdateDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.DeliveryItem;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.LoanItem;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.enums.PendingIssueReason;

/**
 * What confirming a shipment means, in one place: a transfer and a loan between hospitals arrive the same
 * way, and the rules below are the part that would otherwise be written twice and drift apart.
 */
final class ShipmentArrival {

    private ShipmentArrival() {
    }

    /**
     * The day the material got there. Informing it matters because the confirmation is usually made later,
     * from the photo of the signed note - the box arrives on Thursday and this is typed on Saturday.
     *
     * It cannot be in the future, and it cannot be before the shipment left: both would describe a journey
     * that did not happen, and the history is what the traceability of a lot rests on.
     */
    static LocalDate arrivalDay(ShipmentStatusUpdateDTO dto, LocalDateTime dispatchedAt) {
        LocalDate day = dto != null && dto.arrivedOn() != null ? dto.arrivedOn() : LocalDate.now();
        if (day.isAfter(LocalDate.now())) {
            throw new BusinessRuleException("The arrival date cannot be in the future");
        }
        if (dispatchedAt != null && day.isBefore(dispatchedAt.toLocalDate())) {
            throw new BusinessRuleException("The arrival date cannot be before the shipment was dispatched");
        }
        return day;
    }

    /**
     * Quantities the screen informed, by item. A line left out is not a line with zero: it is a line nobody
     * had anything to say about, which means it arrived whole. That is the ordinary case and the reason the
     * screen only sends what came short.
     */
    static Map<Long, Integer> informedQuantities(ShipmentStatusUpdateDTO dto) {
        Map<Long, Integer> byItem = new HashMap<>();
        List<ShipmentStatusUpdateDTO.ArrivedItem> items = dto == null ? null : dto.items();
        if (items == null) return byItem;
        for (ShipmentStatusUpdateDTO.ArrivedItem item : items) {
            if (item.itemId() == null) continue;
            byItem.put(item.itemId(), item.quantity());
        }
        return byItem;
    }

    /**
     * The reason a whole shipment is being written off, required.
     *
     * Everything in the box leaves the stock at once, and a write-off with no reason written down is a
     * record nobody can explain months later - which is exactly when it will be asked about.
     */
    static String requireReason(ShipmentStatusUpdateDTO dto) {
        String why = dto == null || dto.notes() == null ? "" : dto.notes().trim();
        if (why.isEmpty()) {
            throw new BusinessRuleException("Say what happened to the box before writing it off as lost");
        }
        return why.length() > 300 ? why.substring(0, 300) : why;
    }

    /** Keeps the note the shipment already had and adds the write-off reason after it. */
    static String withLossReason(String notes, String why) {
        String base = notes == null || notes.isBlank() ? "" : notes.trim() + " · ";
        String joined = base + "Extravio: " + why;
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }

    /** More units than were sent did not come out of this box. */
    static void requireWithinSent(int received, int sent, String lotNumber) {
        if (received > sent) {
            throw new BusinessRuleException("Lot " + Lot.printedNumber(lotNumber) + ": " + received
                    + " units received, but only " + sent + " were dispatched");
        }
    }

    /**
     * The pending issue for what was not in the box. Its balance stays on the road: nobody is waiting for a
     * second delivery, but what happened is not known yet, and writing it off here would decide that by
     * default. One of the item keys is filled and the other is null, the same way a surgery issue and an
     * entry issue each fill their own.
     */
    static PendingIssue notDelivered(Hospital hospital, Lot lot, int missing, DeliveryItem deliveryItem,
                                     LoanItem loanItem) {
        PendingIssue p = new PendingIssue();
        p.setHospital(hospital);
        p.setDeliveryItem(deliveryItem);
        p.setLoanItem(loanItem);
        p.setEnteredCode(Lot.printedNumber(lot.getNumber()));
        p.setEnteredRef(lot.getMaterial().getRef());
        p.setQuantity(missing);
        p.setReason(PendingIssueReason.NOT_DELIVERED);
        return p;
    }
}

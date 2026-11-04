package com.project.mss.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.loan.LoanDTO;
import com.project.mss.dto.loan.LoanFormDTO;
import com.project.mss.dto.replenishment.ShipmentStatusUpdateDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Loan;
import com.project.mss.model.entity.LoanItem;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.LoanType;
import com.project.mss.model.enums.MovementType;
import com.project.mss.model.enums.ShipmentStatus;
import com.project.mss.repository.LoanRepository;
import com.project.mss.repository.PendingIssueRepository;

/**
 * Each storeroom item is assigned to a hospital, so moving material between
 * hospitals always goes through a loan (the supplier is informed when it is made).
 */
@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final PendingIssueRepository pendingIssueRepository;
    private final StockService stockService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;

    public LoanService(LoanRepository loanRepository, PendingIssueRepository pendingIssueRepository,
                             StockService stockService,
                             MaterialService materialService, AccessControlService accessControlService) {
        this.loanRepository = loanRepository;
        this.pendingIssueRepository = pendingIssueRepository;
        this.stockService = stockService;
        this.materialService = materialService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public LoanDTO create(LoanFormDTO dto) {
        return dto.typeOrLoan() == LoanType.RETURN ? createReturn(dto) : createLoan(dto);
    }

    private LoanDTO createLoan(LoanFormDTO dto) {
        if (dto.destinationHospitalId() == null) {
            throw new BusinessRuleException("Destination hospital is required");
        }
        if (dto.sourceHospitalId().equals(dto.destinationHospitalId())) {
            throw new BusinessRuleException("Source and destination must be different hospitals. "
                    + "To move storeroom material into its own hospital, use replenishment.");
        }
        Hospital source = accessControlService.requireHospitalAccess(dto.sourceHospitalId());
        Hospital destination = accessControlService.requireHospitalAccess(dto.destinationHospitalId());
        if (source.isDistributionCenter() || destination.isDistributionCenter()) {
            throw new BusinessRuleException("Loans cannot involve a distribution center. Use a distribution instead.");
        }

        Loan e = new Loan();
        e.setType(LoanType.LOAN);
        e.setStatus(ShipmentStatus.DISPATCHED);
        e.setSourceHospital(source);
        e.setSourceLocation(dto.sourceLocation());
        e.setDestinationHospital(destination);
        e.setNotes(dto.notes());
        e.setCreatedBy(accessControlService.currentUser());
        e = loanRepository.save(e);

        for (LoanFormDTO.LoanRequestItem item : dto.items()) {
            Lot lot = materialService.findLot(item.lotId());
            // Into the road of the destination, not into its stock: a loan travels between hospitals and
            // only belongs to the destination once someone there confirms the box.
            stockService.transfer(MovementType.LOAN, lot, item.quantity(),
                    source, dto.sourceLocation(), destination, Location.IN_TRANSIT,
                    e.getId(), null, "Loan #" + e.getId());
            e.getItems().add(new LoanItem(e, lot, item.quantity()));
        }
        return LoanDTO.of(loanRepository.save(e));
    }

    /**
     * Return to the supplier: the lots leave the source (inside the hospital or its storeroom; a distribution
     * center only has the storeroom) and enter no other stock. Expired lots may be returned. The reason is required.
     */
    private LoanDTO createReturn(LoanFormDTO dto) {
        accessControlService.requireManager();
        if (dto.returnReason() == null || dto.returnReason().isBlank()) {
            throw new BusinessRuleException("Return reason is required");
        }
        if (dto.destinationHospitalId() != null) {
            throw new BusinessRuleException("A return to the supplier has no destination hospital");
        }
        Hospital source = accessControlService.requireHospitalAccess(dto.sourceHospitalId());
        if (source.isDistributionCenter() && dto.sourceLocation() != Location.STOREROOM) {
            throw new BusinessRuleException(source.getName() + " is a distribution center: return from its storeroom");
        }

        Loan e = new Loan();
        e.setType(LoanType.RETURN);
        // A return to the supplier does not travel through the system: it leaves the stock when it is sent,
        // and Baumer confirms it outside. Born arrived, so it never shows up as a shipment on the road.
        e.setStatus(ShipmentStatus.ARRIVED);
        e.setArrivedOn(java.time.LocalDate.now());
        e.setSourceHospital(source);
        e.setSourceLocation(dto.sourceLocation());
        e.setReturnReason(dto.returnReason().trim());
        e.setNotes(dto.notes());
        e.setCreatedBy(accessControlService.currentUser());
        e = loanRepository.save(e);

        for (LoanFormDTO.LoanRequestItem item : dto.items()) {
            Lot lot = materialService.findLot(item.lotId());
            stockService.recordSupplierReturn(lot, item.quantity(), source, dto.sourceLocation(), e.getId(),
                    "Return to supplier #" + e.getId() + ": " + e.getReturnReason());
            e.getItems().add(new LoanItem(e, lot, item.quantity()));
        }
        return LoanDTO.of(loanRepository.save(e));
    }

    /**
     * Confirms that a loan reached the destination hospital. Same rules as a transfer: a line nobody
     * mentions arrived whole, and what came short stays on the road as a pending issue.
     */
    /**
     * Single state change of a shipment, as the surgery does it: the body says where it should go.
     * A loan that is on the road either arrives or is called off; there is nothing else to do to it.
     */
    @Transactional
    public LoanDTO updateStatus(Long id, ShipmentStatusUpdateDTO dto) {
        return switch (dto.status()) {
            case ARRIVED -> arrive(id, dto);
            case CANCELLED -> cancel(id);
            case LOST -> lost(id, dto);
            case DISPATCHED -> throw new BusinessRuleException(
                    "A shipment is dispatched when it is created; it cannot be sent back to the road");
        };
    }

    private LoanDTO arrive(Long id, ShipmentStatusUpdateDTO dto) {
        accessControlService.requireManager();
        Loan loan = load(id);
        if (loan.getType() != LoanType.LOAN) {
            throw new BusinessRuleException("A return to the supplier does not travel through the system");
        }
        if (loan.getStatus() != ShipmentStatus.DISPATCHED) {
            throw new BusinessRuleException("Loan " + id + " is not on the road: it is " + loan.getStatus());
        }
        LocalDate day = ShipmentArrival.arrivalDay(dto, loan.getCreatedAt());
        Map<Long, Integer> informed = ShipmentArrival.informedQuantities(dto);

        for (LoanItem item : loan.getItems()) {
            int sent = item.getQuantity();
            int received = informed.getOrDefault(item.getId(), sent);
            ShipmentArrival.requireWithinSent(received, sent, item.getLot().getNumber());
            if (received > 0) {
                stockService.recordShipmentArrival(item.getLot(), received, loan.getDestinationHospital(),
                        loan.getId(), null, "Loan #" + loan.getId() + " received");
            }
            item.setArrivedQuantity(received);
            if (received < sent) {
                pendingIssueRepository.save(ShipmentArrival.notDelivered(loan.getDestinationHospital(),
                        item.getLot(), sent - received, null, item));
            }
        }
        loan.setStatus(ShipmentStatus.ARRIVED);
        loan.setArrivedOn(day);
        loan.setArrivedBy(accessControlService.currentUser());
        return LoanDTO.of(loanRepository.save(loan));
    }

    /**
     * Calls off a loan that never left. The material goes back to the exact place it came from - the source
     * hospital's own stock or its storeroom - because anything else would invent a movement.
     */
    /**
     * The box never arrived: everything in it leaves the stock at once, one movement per lot. Same reasoning
     * as the transfer - see DeliveryService.lost - and no pending issue, because what happened is known.
     */
    private LoanDTO lost(Long id, ShipmentStatusUpdateDTO dto) {
        accessControlService.requireManager();
        Loan loan = load(id);
        if (loan.getType() != LoanType.LOAN) {
            throw new BusinessRuleException("A return to the supplier cannot be written off here");
        }
        if (loan.getStatus() != ShipmentStatus.DISPATCHED) {
            throw new BusinessRuleException("Only a loan still on the road can be written off as lost");
        }
        String why = ShipmentArrival.requireReason(dto);
        LocalDate day = ShipmentArrival.arrivalDay(dto, loan.getCreatedAt());
        for (LoanItem item : loan.getItems()) {
            stockService.recordShipmentLoss(item.getLot(), item.getQuantity(), loan.getDestinationHospital(),
                    loan.getId(), null, "Loan #" + loan.getId() + " lost: " + why);
            item.setArrivedQuantity(0);
        }
        loan.setStatus(ShipmentStatus.LOST);
        loan.setArrivedOn(day);
        loan.setArrivedBy(accessControlService.currentUser());
        loan.setNotes(ShipmentArrival.withLossReason(loan.getNotes(), why));
        return LoanDTO.of(loanRepository.save(loan));
    }

    private LoanDTO cancel(Long id) {
        accessControlService.requireManager();
        Loan loan = load(id);
        if (loan.getType() != LoanType.LOAN) {
            throw new BusinessRuleException("A return to the supplier cannot be called off here");
        }
        if (loan.getStatus() != ShipmentStatus.DISPATCHED) {
            throw new BusinessRuleException("Only a loan still on the road can be called off");
        }
        for (LoanItem item : loan.getItems()) {
            stockService.recordShipmentReturn(item.getLot(), item.getQuantity(), loan.getDestinationHospital(),
                    loan.getSourceHospital(), loan.getSourceLocation(), loan.getId(), null,
                    "Loan #" + loan.getId() + " called off");
        }
        loan.setStatus(ShipmentStatus.CANCELLED);
        return LoanDTO.of(loanRepository.save(loan));
    }

    /** Loans still on the road, newest first. */
    @Transactional(readOnly = true)
    public List<LoanDTO> listDispatched() {
        accessControlService.requireManager();
        var allowed = accessControlService.allowedHospitals();
        return loanRepository.findByStatusOrderByCreatedAtDesc(ShipmentStatus.DISPATCHED).stream()
                .filter(e -> e.getType() == LoanType.LOAN)
                .filter(e -> e.getDestinationHospital() != null
                        && (allowed.contains(e.getDestinationHospital().getId())
                            || allowed.contains(e.getSourceHospital().getId())))
                .map(LoanDTO::of).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanDTO> list(LoanType type) {
        var allowed = accessControlService.allowedHospitals();
        return loanRepository.findTop50ByOrderByCreatedAtDesc().stream()
                // Cancelled: registered and put straight back. Listing it next to the ones that happened
                // would make the history claim a movement that was undone in the same breath.
                .filter(e -> e.getStatus() != ShipmentStatus.CANCELLED)
                .filter(e -> type == null || e.getType() == type)
                .filter(e -> allowed.contains(e.getSourceHospital().getId())
                        || (e.getDestinationHospital() != null && allowed.contains(e.getDestinationHospital().getId())))
                .map(LoanDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public Loan load(Long id) {
        Loan e = loanRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Loan " + id + " not found"));
        var allowed = accessControlService.allowedHospitals();
        boolean destinationAllowed = e.getDestinationHospital() != null
                && allowed.contains(e.getDestinationHospital().getId());
        if (!allowed.contains(e.getSourceHospital().getId()) && !destinationAllowed) {
            throw new org.springframework.security.access.AccessDeniedException("No access to this loan");
        }
        return e;
    }
}

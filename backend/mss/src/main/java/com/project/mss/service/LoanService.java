package com.project.mss.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.loan.LoanDTO;
import com.project.mss.dto.loan.LoanFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Loan;
import com.project.mss.model.entity.LoanItem;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.LoanType;
import com.project.mss.model.enums.MovementType;
import com.project.mss.repository.LoanRepository;

/**
 * Each storeroom item is assigned to a hospital, so moving material between
 * hospitals always goes through a loan (the supplier is informed when it is made).
 */
@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final StockService stockService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;

    public LoanService(LoanRepository loanRepository, StockService stockService,
                             MaterialService materialService, AccessControlService accessControlService) {
        this.loanRepository = loanRepository;
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
        e.setSourceHospital(source);
        e.setSourceLocation(dto.sourceLocation());
        e.setDestinationHospital(destination);
        e.setNotes(dto.notes());
        e.setCreatedBy(accessControlService.currentUser());
        e = loanRepository.save(e);

        for (LoanFormDTO.LoanRequestItem item : dto.items()) {
            Lot lot = materialService.findLot(item.lotId());
            stockService.transfer(MovementType.LOAN, lot, item.quantity(),
                    source, dto.sourceLocation(), destination, Location.HOSPITAL,
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

    @Transactional(readOnly = true)
    public List<LoanDTO> list(LoanType type) {
        var allowed = accessControlService.allowedHospitals();
        return loanRepository.findTop50ByOrderByCreatedAtDesc().stream()
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

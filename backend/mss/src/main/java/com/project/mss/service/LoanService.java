package com.project.mss.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.loan.LoanDTO;
import com.project.mss.dto.loan.LoanStatusUpdateDTO;
import com.project.mss.dto.loan.LoanFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Loan;
import com.project.mss.model.entity.LoanItem;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.LoanStatus;
import com.project.mss.model.enums.MovementType;
import com.project.mss.repository.LoanRepository;

/**
 * Each storeroom item is assigned to a hospital, so moving material between
 * hospitals always goes through a loan, which stays pending until the supplier is notified.
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

    /** Applies a status transition requested through PATCH /loans/{id}. */
    @Transactional
    public LoanDTO updateStatus(Long id, LoanStatusUpdateDTO dto) {
        accessControlService.requireManager();
        Loan e = load(id);
        switch (dto.status()) {
            case NOTIFIED -> {
                if (e.getStatus() == LoanStatus.NOTIFIED) {
                    throw new BusinessRuleException("Loan " + id + " was already notified");
                }
                e.setStatus(LoanStatus.NOTIFIED);
                e.setNotifiedAt(LocalDateTime.now());
            }
            case PENDING_NOTIFICATION -> throw new BusinessRuleException("A notified loan cannot return to pending");
        }
        return LoanDTO.of(loanRepository.save(e));
    }

    @Transactional(readOnly = true)
    public List<LoanDTO> list(LoanStatus status) {
        var allowed = accessControlService.allowedHospitals();
        List<Loan> list = status != null
                ? loanRepository.findByStatusOrderByCreatedAtDesc(status)
                : loanRepository.findTop50ByOrderByCreatedAtDesc();
        return list.stream()
                .filter(e -> allowed.contains(e.getSourceHospital().getId())
                        || allowed.contains(e.getDestinationHospital().getId()))
                .map(LoanDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public Loan load(Long id) {
        Loan e = loanRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Loan " + id + " not found"));
        var allowed = accessControlService.allowedHospitals();
        if (!allowed.contains(e.getSourceHospital().getId()) && !allowed.contains(e.getDestinationHospital().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("No access to this loan");
        }
        return e;
    }
}

package com.project.mss.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.surgery.PendingIssueDTO;
import com.project.mss.dto.surgery.PendingIssueStatusUpdateDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.enums.ReadSource;
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.repository.PendingIssueRepository;

@Service
public class PendingIssueService {

    private final PendingIssueRepository pendingIssueRepository;
    private final SurgeryService surgeryService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;

    public PendingIssueService(PendingIssueRepository pendingIssueRepository, SurgeryService surgeryService,
                            MaterialService materialService, AccessControlService accessControlService) {
        this.pendingIssueRepository = pendingIssueRepository;
        this.surgeryService = surgeryService;
        this.materialService = materialService;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<PendingIssueDTO> list(PendingIssueStatus status) {
        var allowed = accessControlService.allowedHospitals();
        return pendingIssueRepository.findByStatusOrderByCreatedAtDesc(status != null ? status : PendingIssueStatus.OPEN)
                .stream()
                .filter(p -> allowed.contains(p.getHospital().getId()))
                .map(PendingIssueDTO::of)
                .toList();
    }

    /**
     * Applies a status transition requested through PATCH /pending-issues/{id}.
     * RESOLVED: records the given lot in the surgery (debiting the hospital stock).
     * DISCARDED: closes the issue with the justification, without touching the stock.
     */
    @Transactional
    public PendingIssueDTO updateStatus(Long id, PendingIssueStatusUpdateDTO dto) {
        accessControlService.requireManager();
        PendingIssue p = pendingIssueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pending issue " + id + " not found"));
        if (p.getStatus() != PendingIssueStatus.OPEN) {
            throw new BusinessRuleException("Pending issue " + id + " was already handled");
        }
        switch (dto.status()) {
            case RESOLVED -> resolve(p, dto.lotId());
            case DISCARDED -> {
                if (dto.lotId() != null) {
                    throw new BusinessRuleException("A discarded pending issue cannot have a lot. Use RESOLVED instead.");
                }
            }
            case OPEN -> throw new BusinessRuleException("A pending issue cannot be reopened");
        }
        p.setStatus(dto.status());
        p.setResolution(dto.resolution());
        p.setResolvedAt(LocalDateTime.now());
        p.setResolvedBy(accessControlService.currentUser());
        return PendingIssueDTO.of(pendingIssueRepository.save(p));
    }

    /** Records the correct lot in the surgery linked to the pending issue. */
    private void resolve(PendingIssue p, Long lotId) {
        if (lotId == null) {
            throw new BusinessRuleException("lotId is required to resolve a pending issue");
        }
        if (p.getSurgery() == null) {
            throw new BusinessRuleException("Pending issue without a linked surgery; it can only be discarded");
        }
        Lot lot = materialService.findLot(lotId);
        var surgery = surgeryService.load(p.getSurgery().getId());
        ReadSource readSource = p.getReadSource() != null ? p.getReadSource() : ReadSource.MANUAL;
        surgeryService.recordLot(surgery, lot, p.getQuantity(), readSource);
    }
}

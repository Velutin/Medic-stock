package com.project.mss.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.surgery.PendingIssueDTO;
import com.project.mss.dto.surgery.ResolvePendingIssueDTO;
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
     * With lotId: records the correct lot in the surgery (debiting the hospital stock) and marks it RESOLVED.
     * Without lotId: marks it DISCARDED with the justification.
     */
    @Transactional
    public PendingIssueDTO resolve(Long id, ResolvePendingIssueDTO dto) {
        accessControlService.requireManager();
        PendingIssue p = pendingIssueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pending issue " + id + " not found"));
        if (p.getStatus() != PendingIssueStatus.OPEN) {
            throw new BusinessRuleException("Pending issue " + id + " was already handled");
        }
        if (dto.lotId() != null) {
            if (p.getSurgery() == null) {
                throw new BusinessRuleException("Pending issue without a linked surgery; it can only be discarded");
            }
            Lot lot = materialService.findLot(dto.lotId());
            var surgery = surgeryService.load(p.getSurgery().getId());
            ReadSource readSource = p.getReadSource() != null ? p.getReadSource() : ReadSource.MANUAL;
            surgeryService.recordLot(surgery, lot, p.getQuantity(), readSource);
            p.setStatus(PendingIssueStatus.RESOLVED);
        } else {
            p.setStatus(PendingIssueStatus.DISCARDED);
        }
        p.setResolution(dto.resolution());
        p.setResolvedAt(LocalDateTime.now());
        p.setResolvedBy(accessControlService.currentUser());
        return PendingIssueDTO.of(pendingIssueRepository.save(p));
    }
}

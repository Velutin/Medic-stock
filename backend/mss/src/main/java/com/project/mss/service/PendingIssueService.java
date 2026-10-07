package com.project.mss.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.material.ScannedCodeDTO;
import com.project.mss.dto.surgery.PendingIssueDTO;
import com.project.mss.dto.surgery.PendingLotOptionDTO;
import com.project.mss.dto.surgery.PendingIssueStatusUpdateDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.entity.Stock;
import com.project.mss.model.enums.ReadSource;
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.repository.LotRepository;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.PendingIssueRepository;

@Service
public class PendingIssueService {

    private final PendingIssueRepository pendingIssueRepository;
    private final SurgeryService surgeryService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;
    private final LotScanService lotScanService;
    private final LotRepository lotRepository;
    private final MaterialRepository materialRepository;
    private final StockService stockService;

    public PendingIssueService(PendingIssueRepository pendingIssueRepository, SurgeryService surgeryService,
                            MaterialService materialService, AccessControlService accessControlService,
                            LotScanService lotScanService, LotRepository lotRepository,
                            MaterialRepository materialRepository, StockService stockService) {
        this.pendingIssueRepository = pendingIssueRepository;
        this.surgeryService = surgeryService;
        this.materialService = materialService;
        this.accessControlService = accessControlService;
        this.lotScanService = lotScanService;
        this.lotRepository = lotRepository;
        this.materialRepository = materialRepository;
        this.stockService = stockService;
    }

    /**
     * Lots to resolve a pending issue, as the surgery screen shows them: the valid lots inside the hospital of the
     * material(s) identified by what was read (GTIN, REF, or registered lots with the number read). Lots with the
     * number read come first. Empty when nothing is identified: the screen then offers a search.
     */
    @Transactional(readOnly = true)
    public List<PendingLotOptionDTO> suggestions(Long id) {
        accessControlService.requireManager();
        PendingIssue p = pendingIssueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pending issue " + id + " not found"));
        Hospital hospital = accessControlService.requireHospitalAccess(p.getHospital().getId());
        LocalDate date = p.getSurgery() != null ? p.getSurgery().getSurgeryDate() : LocalDate.now();
        String code = p.getEnteredCode() == null ? "" : p.getEnteredCode().trim();

        String number = null;
        Set<Long> materials = new LinkedHashSet<>();
        if (!code.isEmpty()) {
            try {
                ScannedCodeDTO read = lotScanService.read(code);
                if (read.material() != null) materials.add(read.material().id());
                number = read.lot() != null ? read.lot()
                        : read.gtin() == null && read.material() == null ? code.toUpperCase() : null;
            } catch (RuntimeException unreadable) {
                number = code.toUpperCase();
            }
        }
        if (p.getEnteredRef() != null && !p.getEnteredRef().isBlank()) {
            materialRepository.findByRefIgnoreCase(p.getEnteredRef().trim()).ifPresent(m -> materials.add(m.getId()));
        }
        if (number != null) {
            lotRepository.findByNumber(number).forEach(l -> materials.add(l.getMaterial().getId()));
        }

        String readNumber = number;
        List<PendingLotOptionDTO> out = new ArrayList<>();
        for (Long materialId : materials) {
            for (Stock s : stockService.lotsInsideHospital(hospital, materialId, date)) {
                Lot l = s.getLot();
                Material m = l.getMaterial();
                String name = m.getComponent() != null && !m.getComponent().isBlank()
                        ? m.getComponent() + (m.getSize() != null && !m.getSize().isBlank() ? " · " + m.getSize() : "")
                        : m.getDescription();
                out.add(new PendingLotOptionDTO(l.getId(), m.getRef(), name, l.getNumber(), l.getExpiryDate(),
                        s.getQuantity(), readNumber != null && l.hasNumber(readNumber)));
            }
        }
        out.sort(Comparator.comparing((PendingLotOptionDTO o) -> !o.sameLot())
                .thenComparing(PendingLotOptionDTO::ref).thenComparing(PendingLotOptionDTO::expiryDate));
        return out;
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

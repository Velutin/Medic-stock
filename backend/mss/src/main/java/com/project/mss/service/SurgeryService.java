package com.project.mss.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.project.mss.dto.surgery.SurgeryDTO;
import com.project.mss.dto.surgery.SurgeryStatusUpdateDTO;
import com.project.mss.dto.surgery.SurgeryFormDTO;
import com.project.mss.dto.surgery.SurgeryItemDTO;
import com.project.mss.dto.surgery.SurgerySummaryDTO;
import com.project.mss.dto.surgery.SheetItemsDTO;
import com.project.mss.dto.surgery.PendingIssueDTO;
import com.project.mss.dto.surgery.WithdrawalResultDTO;
import com.project.mss.dto.surgery.WithdrawalItemDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Surgery;
import com.project.mss.model.entity.SurgeryItem;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.entity.User;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.PendingIssueReason;
import com.project.mss.model.enums.ReadSource;
import com.project.mss.model.enums.SurgeryStatus;
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.repository.SurgeryRepository;
import com.project.mss.repository.PendingIssueRepository;
import com.project.mss.repository.UserRepository;

/**
 * Material withdrawal in surgeries. Material leaves the stock INSIDE the surgery hospital
 * and the value comes from that hospital's price table. Any scan that cannot be recorded
 * safely becomes a pending issue for review, without touching the balance.
 */
@Service
public class SurgeryService {

    private final SurgeryRepository surgeryRepository;
    private final PendingIssueRepository pendingIssueRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final MaterialService materialService;
    private final LotScanService lotScanService;
    private final FileStorageService fileStorageService;
    private final AccessControlService accessControlService;

    public SurgeryService(SurgeryRepository surgeryRepository, PendingIssueRepository pendingIssueRepository,
                           UserRepository userRepository, StockService stockService,
                           MaterialService materialService, LotScanService lotScanService,
                           FileStorageService fileStorageService, AccessControlService accessControlService) {
        this.surgeryRepository = surgeryRepository;
        this.pendingIssueRepository = pendingIssueRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.materialService = materialService;
        this.lotScanService = lotScanService;
        this.fileStorageService = fileStorageService;
        this.accessControlService = accessControlService;
    }

    // ============================================================ cadastro

    @Transactional
    public SurgeryDTO create(SurgeryFormDTO dto) {
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());
        if (hospital.isDistributionCenter()) {
            throw new BusinessRuleException(hospital.getName() + " is a distribution center and cannot have surgeries");
        }
        User current = accessControlService.currentUser();

        User surgicalTech = current;
        if (dto.surgicalTechId() != null && !dto.surgicalTechId().equals(current.getId())) {
            if (!current.isManager()) {
                throw new AccessDeniedException("Only administrators can record surgeries for another surgical tech");
            }
            surgicalTech = userRepository.findById(dto.surgicalTechId())
                    .orElseThrow(() -> new EntityNotFoundException("Surgical tech " + dto.surgicalTechId() + " not found"));
        }

        Surgery c = new Surgery();
        c.setHospital(hospital);
        c.setSurgicalTech(surgicalTech);
        c.setPatientName(dto.patientName().trim());
        c.setSurgeryDate(dto.surgeryDate());
        c.setDoctor(dto.doctor());
        c.setNotes(dto.notes());
        c.setCreatedBy(current);
        return toDTO(surgeryRepository.save(c));
    }

    @Transactional(readOnly = true)
    public SurgeryDTO find(Long id) {
        return toDTO(load(id));
    }

    @Transactional(readOnly = true)
    public Page<SurgerySummaryDTO> list(Long hospitalId, LocalDate start, LocalDate end, Pageable pageable) {
        Set<Long> hospitals = hospitalId != null
                ? Set.of(accessControlService.requireHospitalAccess(hospitalId).getId())
                : accessControlService.allowedHospitals();
        if (hospitals.isEmpty()) return Page.empty(pageable);
        LocalDate i = start != null ? start : LocalDate.now().minusDays(30);
        LocalDate f = end != null ? end : LocalDate.now();
        boolean showValues = accessControlService.canSeeValues();
        return surgeryRepository.list(hospitals, i, f, pageable).map(c -> SurgerySummaryDTO.of(c, showValues));
    }

    // ============================================================ material withdrawal

    /** Records an item scanned by QR code, barcode or typed lot number. */
    @Transactional
    public WithdrawalResultDTO recordWithdrawal(Long surgeryId, WithdrawalItemDTO dto) {
        Surgery c = loadEditable(surgeryId);
        return recordScan(c, dto.code(), dto.ref(), dto.readSource(), dto.quantityOrOne());
    }

    /** Records labels extracted from the consumption sheet. Each label counts as 1 item. */
    @Transactional
    public List<WithdrawalResultDTO> recordSheet(Long surgeryId, SheetItemsDTO dto) {
        Surgery c = loadEditable(surgeryId);
        return dto.labels().stream()
                .filter(e -> e.lot() != null && !e.lot().isBlank())
                .map(e -> recordScan(c, e.lot(), e.ref(), ReadSource.CONSUMPTION_SHEET, 1))
                .toList();
    }

    @Transactional
    public SurgeryDTO removeItem(Long surgeryId, Long itemId) {
        Surgery c = loadEditable(surgeryId);
        SurgeryItem item = c.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Item " + itemId + " does not belong to the surgery"));
        stockService.reverseSurgeryWithdrawal(item.getLot(), item.getQuantity(), c.getHospital(), c.getId(),
                "Item removed from surgery");
        c.getItems().remove(item);
        c.recalculateTotal();
        return toDTO(surgeryRepository.save(c));
    }

    /** Applies a status transition requested through PATCH /surgeries/{id}. */
    @Transactional
    public SurgeryDTO updateStatus(Long id, SurgeryStatusUpdateDTO dto) {
        return switch (dto.status()) {
            case COMPLETED -> complete(id);
            case CANCELLED -> {
                if (dto.cancellationReason() == null || dto.cancellationReason().isBlank()) {
                    throw new BusinessRuleException("Cancellation reason is required");
                }
                yield cancel(id, dto.cancellationReason().trim());
            }
            case OPEN -> throw new BusinessRuleException("A surgery cannot be reopened");
        };
    }

    private SurgeryDTO complete(Long id) {
        Surgery c = loadOpen(id);
        if (c.getItems().isEmpty()) {
            throw new BusinessRuleException("The surgery has no recorded items");
        }
        if (c.getSheetFile() == null) {
            throw new BusinessRuleException("Attach the consumption sheet (PDF or photos) before completing the surgery");
        }
        long open = pendingIssueRepository.countBySurgeryIdAndStatus(id, PendingIssueStatus.OPEN);
        if (open > 0) {
            throw new BusinessRuleException("There are " + open + " open pending issue(s) in this surgery. "
                    + "Resolve or discard them before completing.");
        }
        c.setStatus(SurgeryStatus.COMPLETED);
        c.setCompletedAt(LocalDateTime.now());
        return toDTO(surgeryRepository.save(c));
    }

    /**
     * Cancels the surgery: returns all items to the hospital stock and discards the pending issues.
     * Items and total are kept as a record of what had been recorded; reports ignore cancelled surgeries.
     */
    private SurgeryDTO cancel(Long id, String reason) {
        Surgery c = load(id);
        if (c.getStatus() == SurgeryStatus.CANCELLED) {
            throw new BusinessRuleException("The surgery is already cancelled");
        }
        if (c.getStatus() == SurgeryStatus.COMPLETED) {
            accessControlService.requireManager();
        }
        String movementNotes = "Surgery cancellation: " + reason;
        for (SurgeryItem item : c.getItems()) {
            stockService.reverseSurgeryWithdrawal(item.getLot(), item.getQuantity(), c.getHospital(), c.getId(), movementNotes);
        }
        c.setStatus(SurgeryStatus.CANCELLED);
        c.setCancelledAt(LocalDateTime.now());
        c.setCancelledBy(accessControlService.currentUser());
        c.setCancellationReason(reason);
        pendingIssueRepository.findBySurgeryIdOrderByIdAsc(id).stream()
                .filter(p -> p.getStatus() == PendingIssueStatus.OPEN)
                .forEach(p -> {
                    p.setStatus(PendingIssueStatus.DISCARDED);
                    p.setResolution("Surgery cancelled");
                    pendingIssueRepository.save(p);
                });
        return toDTO(surgeryRepository.save(c));
    }

    // ============================================================ consumption sheet

    @Transactional
    public SurgeryDTO attachSheet(Long id, List<MultipartFile> files) {
        Surgery c = load(id);
        c.setSheetFile(fileStorageService.saveSheet(id, files));
        return toDTO(surgeryRepository.save(c));
    }

    @Transactional(readOnly = true)
    public byte[] downloadSheet(Long id) {
        Surgery c = load(id);
        if (c.getSheetFile() == null) throw new EntityNotFoundException("Surgery has no attached sheet");
        return fileStorageService.read(c.getSheetFile());
    }

    // ============================================================ uso interno

    /** Records an already identified lot (used when resolving pending issues). */
    @Transactional
    public SurgeryItemDTO recordLot(Surgery c, Lot lot, int quantity, ReadSource readSource) {
        requireEditable(c);
        stockService.recordSurgeryWithdrawal(lot, quantity, c.getHospital(), c.getId());
        SurgeryItem item = new SurgeryItem();
        item.setSurgery(c);
        item.setLot(lot);
        item.setQuantity(quantity);
        item.setReadSource(readSource);
        item.setUnitValue(materialService.hospitalValue(c.getHospital().getId(), lot.getMaterial().getId())
                .orElse(null));
        c.getItems().add(item);
        c.recalculateTotal();
        surgeryRepository.saveAndFlush(c);
        return SurgeryItemDTO.of(item, accessControlService.canSeeValues());
    }

    public Surgery load(Long id) {
        Surgery c = surgeryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Surgery " + id + " not found"));
        accessControlService.requireHospitalAccess(c.getHospital().getId());
        return c;
    }

    /**
     * Items can be added or removed while the surgery is open and, by administrators, at any time
     * after it was completed (corrections). Cancelled surgeries are never changed.
     */
    private Surgery loadEditable(Long id) {
        Surgery c = load(id);
        requireEditable(c);
        return c;
    }

    private void requireEditable(Surgery c) {
        switch (c.getStatus()) {
            case OPEN -> { }
            case COMPLETED -> {
                if (!accessControlService.canSeeValues()) {
                    throw new BusinessRuleException("Surgery " + c.getId()
                            + " is completed: only administrators can change its items");
                }
            }
            case CANCELLED -> throw new BusinessRuleException("Surgery " + c.getId() + " is cancelled and cannot be changed");
        }
    }

    private Surgery loadOpen(Long id) {
        Surgery c = load(id);
        if (c.getStatus() != SurgeryStatus.OPEN) {
            throw new BusinessRuleException("Surgery " + id + " is " + c.getStatus().name().toLowerCase()
                    + " and cannot be changed");
        }
        return c;
    }

    private WithdrawalResultDTO recordScan(Surgery c, String code, String ref, ReadSource readSource, int qty) {
        var scan = lotScanService.resolve(code, ref, c.getHospital());

        switch (scan.status()) {
            case NOT_FOUND -> {
                return pendingIssue(c, code, scan.parsedRef(), qty, readSource, PendingIssueReason.LOT_NOT_FOUND);
            }
            case AMBIGUOUS -> {
                return pendingIssue(c, code, scan.parsedRef(), qty, readSource, PendingIssueReason.AMBIGUOUS_LOT);
            }
            default -> { /* ENCONTRADO */ }
        }

        Lot lot = scan.lot();
        if (lot.isExpired(c.getSurgeryDate())) {
            return pendingIssue(c, code, lot.getMaterial().getRef(), qty, readSource, PendingIssueReason.EXPIRED_LOT);
        }
        int balance = stockService.balance(lot, c.getHospital(), Location.HOSPITAL);
        if (balance < qty) {
            return pendingIssue(c, code, lot.getMaterial().getRef(), qty, readSource, PendingIssueReason.NO_HOSPITAL_BALANCE);
        }

        SurgeryItemDTO item = recordLot(c, lot, qty, readSource);
        String warning = Boolean.TRUE.equals(item.missingPrice())
                ? "REF " + item.ref() + " has no value in the price table of " + c.getHospital().getName()
                        + ". The value is filled in automatically when it is registered."
                : null;
        return new WithdrawalResultDTO(true, item, null, warning);
    }

    private WithdrawalResultDTO pendingIssue(Surgery c, String code, String ref, int qty, ReadSource readSource,
                                        PendingIssueReason reason) {
        PendingIssue p = new PendingIssue();
        p.setSurgery(c);
        p.setHospital(c.getHospital());
        p.setEnteredCode(code.length() > 200 ? code.substring(0, 200) : code);
        p.setEnteredRef(ref);
        p.setQuantity(qty);
        p.setReadSource(readSource);
        p.setReason(reason);
        p = pendingIssueRepository.save(p);
        String warning = switch (reason) {
            case LOT_NOT_FOUND -> "Lot not found. Recorded as a pending issue for review.";
            case AMBIGUOUS_LOT -> "This lot number matches more than one material or expiry date with balance in the hospital. "
                    + "Scan the GS1 code (with expiry date), provide the REF or resolve the pending issue.";
            case EXPIRED_LOT -> "Lot expired on the surgery date. Recorded as a pending issue.";
            case NO_HOSPITAL_BALANCE -> "Lot has no balance inside the hospital. Recorded as a pending issue.";
        };
        return new WithdrawalResultDTO(false, null, PendingIssueDTO.of(p), warning);
    }

    private SurgeryDTO toDTO(Surgery c) {
        List<PendingIssueDTO> pendingIssues = c.getId() == null ? List.of()
                : pendingIssueRepository.findBySurgeryIdOrderByIdAsc(c.getId()).stream().map(PendingIssueDTO::of).toList();
        return SurgeryDTO.of(c, pendingIssues, accessControlService.canSeeValues());
    }

}

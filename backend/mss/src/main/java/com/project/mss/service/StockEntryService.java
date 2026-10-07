package com.project.mss.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.entry.EntryDTO;
import com.project.mss.dto.entry.EntryFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.StockEntry;
import com.project.mss.model.entity.StockEntryItem;
import com.project.mss.model.enums.Location;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.LotRepository;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.StockEntryRepository;

/**
 * Stock entries: material received from the supplier always enters the storeroom, assigned to a regular
 * hospital or to a distribution center (e.g. SESAB). Hospitals supplied by a distribution center do not
 * receive entries: their material arrives through the center.
 *
 * An entry can be corrected as a whole (destination, date, REF, lot, expiry date, quantity). The correction
 * only applies the difference between the old and the new content, and is refused when the material to be
 * removed has already left the storeroom (delivered to the hospital or adjusted).
 *
 * A lot number belongs to only one REF: a number already registered with another REF enters only after the user
 * confirms the REF change of those lots (item.changeRef), done by {@link LotRefChangeService}.
 */
@Service
public class StockEntryService {

    /** Lot resolved from the form, with the total quantity received. */
    private record ResolvedItem(Lot lot, int quantity) { }

    /** Balance difference of one lot in one storeroom, produced by a correction. */
    private static final class Correction {
        private final Hospital hospital;
        private final Lot lot;
        private int difference;

        private Correction(Hospital hospital, Lot lot) {
            this.hospital = hospital;
            this.lot = lot;
        }
    }

    private final StockEntryRepository stockEntryRepository;
    private final HospitalRepository hospitalRepository;
    private final MaterialRepository materialRepository;
    private final MaterialService materialService;
    private final StockService stockService;
    private final AccessControlService accessControlService;
    private final LotRepository lotRepository;
    private final LotRefChangeService lotRefChangeService;

    public StockEntryService(StockEntryRepository stockEntryRepository, HospitalRepository hospitalRepository,
                             MaterialRepository materialRepository, MaterialService materialService,
                             StockService stockService, AccessControlService accessControlService,
                             LotRepository lotRepository, LotRefChangeService lotRefChangeService) {
        this.stockEntryRepository = stockEntryRepository;
        this.hospitalRepository = hospitalRepository;
        this.materialRepository = materialRepository;
        this.materialService = materialService;
        this.stockService = stockService;
        this.accessControlService = accessControlService;
        this.lotRepository = lotRepository;
        this.lotRefChangeService = lotRefChangeService;
    }

    @Transactional
    public EntryDTO create(EntryFormDTO dto) {
        accessControlService.requireManager();
        applyRefChanges(dto.items(), Set.of());
        Hospital destination = requireDestination(dto.hospitalId());
        Map<Long, ResolvedItem> items = resolveItems(dto.items());

        StockEntry entry = new StockEntry();
        entry.setHospital(destination);
        entry.setEntryDate(dto.entryDate());
        entry.setNotes(blankToNull(dto.notes()));
        entry.setCreatedBy(accessControlService.currentUser());
        entry = stockEntryRepository.save(entry);

        LocalDateTime movementDate = LocalDateTime.of(dto.entryDate(), LocalTime.now());
        for (ResolvedItem item : items.values()) {
            stockService.recordStockEntry(item.lot(), item.quantity(), destination, entry.getId(), movementDate);
            entry.getItems().add(new StockEntryItem(entry, item.lot(), item.quantity()));
        }
        return EntryDTO.of(stockEntryRepository.save(entry));
    }

    /** Replaces the content of the entry, applying only the balance differences to the storerooms. */
    @Transactional
    public EntryDTO update(Long id, EntryFormDTO dto) {
        accessControlService.requireManager();
        // Items the entry already had keep their lot as it is (the rule applies to what is added or changed)
        Set<String> kept = new HashSet<>();
        load(id).getItems().forEach(i -> kept.add(lotKey(i.getLot().getMaterial().getId(), i.getLot().getNumber())));
        applyRefChanges(dto.items(), kept);
        StockEntry entry = load(id);
        Hospital previous = entry.getHospital();
        Hospital destination = requireDestination(dto.hospitalId());
        Map<Long, ResolvedItem> wanted = resolveItems(dto.items());

        // Old items leave the previous destination's storeroom; new items enter the new destination's storeroom
        Map<String, Correction> corrections = new LinkedHashMap<>();
        for (StockEntryItem old : entry.getItems()) {
            corrections.computeIfAbsent(key(previous, old.getLot()), k -> new Correction(previous, old.getLot()))
                    .difference -= old.getQuantity();
        }
        for (ResolvedItem item : wanted.values()) {
            corrections.computeIfAbsent(key(destination, item.lot()), k -> new Correction(destination, item.lot()))
                    .difference += item.quantity();
        }

        // Removals first: they fail when the material already left the storeroom, before anything is credited
        for (Correction c : corrections.values()) {
            if (c.difference >= 0) continue;
            int available = stockService.balance(c.lot, c.hospital, Location.STOREROOM);
            if (available < -c.difference) {
                throw new BusinessRuleException(String.format(
                        "Lot %s (REF %s) of entry #%d already left the storeroom of %s: available %d, the correction removes %d",
                        c.lot.getNumber(), c.lot.getMaterial().getRef(), entry.getId(), c.hospital.getName(),
                        available, -c.difference));
            }
            stockService.correctStockEntry(c.lot, c.difference, c.hospital, entry.getId());
        }
        for (Correction c : corrections.values()) {
            if (c.difference > 0) stockService.correctStockEntry(c.lot, c.difference, c.hospital, entry.getId());
        }

        // Items updated in place (a lot appears only once per entry)
        Map<Long, StockEntryItem> current = new HashMap<>();
        entry.getItems().forEach(i -> current.put(i.getLot().getId(), i));
        entry.getItems().removeIf(i -> !wanted.containsKey(i.getLot().getId()));
        for (ResolvedItem item : wanted.values()) {
            StockEntryItem existing = current.get(item.lot().getId());
            if (existing != null) {
                existing.setQuantity(item.quantity());
            } else {
                entry.getItems().add(new StockEntryItem(entry, item.lot(), item.quantity()));
            }
        }

        entry.setHospital(destination);
        entry.setEntryDate(dto.entryDate());
        entry.setNotes(blankToNull(dto.notes()));
        entry.setUpdatedBy(accessControlService.currentUser());
        entry.setUpdatedAt(LocalDateTime.now());
        return EntryDTO.of(stockEntryRepository.save(entry));
    }

    /** Entries in a period (default: everything until today), newest first. */
    @Transactional(readOnly = true)
    public Page<EntryDTO> list(Long hospitalId, LocalDate start, LocalDate end, Pageable pageable) {
        accessControlService.requireManager();
        Collection<Long> hospitals = hospitalId != null
                ? List.of(accessControlService.requireHospitalAccess(hospitalId).getId())
                : accessControlService.allowedHospitals();
        if (hospitals.isEmpty()) return Page.empty(pageable);
        LocalDate from = start != null ? start : LocalDate.of(2000, 1, 1);
        LocalDate to = end != null ? end : LocalDate.now();
        if (from.isAfter(to)) throw new BusinessRuleException("The start date must be before the end date");
        return stockEntryRepository.list(hospitals, from, to, pageable).map(EntryDTO::of);
    }

    @Transactional(readOnly = true)
    public EntryDTO find(Long id) {
        accessControlService.requireManager();
        return EntryDTO.of(load(id));
    }

    // ============================================================ helpers

    /**
     * One REF per lot number: refuses the same number with two REFs in the request and, for a number already
     * registered with another REF, changes those lots to this REF when the user confirmed it (changeRef).
     */
    private void applyRefChanges(List<EntryFormDTO.Item> items, Set<String> kept) {
        Map<String, Long> materialByNumber = new LinkedHashMap<>();
        Map<String, Boolean> confirmed = new HashMap<>();
        for (EntryFormDTO.Item item : items) {
            // Compared without leading zeros: 005706061 and 5706061 are the same lot
            String number = Lot.comparableNumber(item.lot());
            Long previous = materialByNumber.putIfAbsent(number, item.materialId());
            if (previous != null && !previous.equals(item.materialId())) {
                throw new BusinessRuleException(String.format(
                        "Lot %s appears with more than one REF in this entry: a lot number belongs to only one REF", number));
            }
            confirmed.merge(number, Boolean.TRUE.equals(item.changeRef()), Boolean::logicalOr);
        }
        for (Map.Entry<String, Long> e : materialByNumber.entrySet()) {
            String number = e.getKey();
            Long materialId = e.getValue();
            List<Lot> others = lotRefChangeService.conflicts(materialId, number);
            if (others.isEmpty()) continue;
            boolean change = confirmed.get(number);
            if (!change && kept.contains(lotKey(materialId, number))) continue;
            if (!change) {
                String refs = others.stream().map(l -> l.getMaterial().getRef()).distinct().collect(Collectors.joining(", "));
                throw new BusinessRuleException(String.format(
                        "Lot %s is already registered with REF %s: confirm the REF change of those lots to receive it", number, refs));
            }
            lotRefChangeService.changeRef(materialId, number);
        }
    }

    private static String lotKey(Long materialId, String number) {
        return materialId + ":" + Lot.comparableNumber(number);
    }

    private StockEntry load(Long id) {
        StockEntry entry = stockEntryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Stock entry " + id + " not found"));
        accessControlService.requireHospitalAccess(entry.getHospital().getId());
        return entry;
    }

    /** Active regular hospital or distribution center; hospitals supplied by a center receive through it. */
    private Hospital requireDestination(Long hospitalId) {
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        if (!Boolean.TRUE.equals(hospital.getActive())) {
            throw new BusinessRuleException(hospital.getName() + " is inactive");
        }
        hospitalRepository.findCenterOf(hospitalId).ifPresent(center -> {
            throw new BusinessRuleException(hospital.getName() + " is supplied by " + center.getName()
                    + ": register the entry at " + center.getName());
        });
        return hospital;
    }

    /**
     * Finds or creates each lot, refuses expired ones and sums repeated lots. An expiry typed as MM/AAAA reuses the
     * lot with the same number and expiry month (e.g. registered before from a QR code with the full date).
     */
    private Map<Long, ResolvedItem> resolveItems(List<EntryFormDTO.Item> items) {
        LocalDate today = LocalDate.now();
        Map<Long, ResolvedItem> result = new LinkedHashMap<>();
        for (EntryFormDTO.Item item : items) {
            Material material = materialRepository.findById(item.materialId())
                    .orElseThrow(() -> new EntityNotFoundException("Material " + item.materialId() + " not found"));
            String number = item.lot().trim().toUpperCase();
            Lot lot = Boolean.TRUE.equals(item.monthOnly()) ? sameMonthLot(material, number, item.expiryDate()) : null;
            LocalDate expiry = lot != null ? lot.getExpiryDate() : item.expiryDate();
            if (expiry.isBefore(today)) {
                throw new BusinessRuleException(String.format("Lot %s (REF %s) is expired and cannot be received",
                        number, material.getRef()));
            }
            if (lot == null) lot = materialService.getOrCreateLot(material, item.lot(), item.expiryDate());
            Lot resolved = lot;
            result.merge(resolved.getId(), new ResolvedItem(resolved, item.quantity()),
                    (a, b) -> new ResolvedItem(a.lot(), a.quantity() + b.quantity()));
        }
        return result;
    }

    /** Existing lot of the material with this number and expiry month; null when there is none. */
    private Lot sameMonthLot(Material material, String number, LocalDate expiry) {
        YearMonth month = YearMonth.from(expiry);
        List<Lot> sameMonth = lotRepository.findByMaterialIdAndNumberIgnoreCaseOrderByExpiryDateAsc(material.getId(), number)
                .stream().filter(l -> YearMonth.from(l.getExpiryDate()).equals(month)).toList();
        if (sameMonth.size() > 1) {
            throw new BusinessRuleException(String.format(
                    "Lot %s (REF %s) has more than one expiry date in %02d/%d: type the full date (DD/MM/AAAA) or read the QR code",
                    number, material.getRef(), month.getMonthValue(), month.getYear()));
        }
        return sameMonth.isEmpty() ? null : sameMonth.get(0);
    }

    private static String key(Hospital hospital, Lot lot) {
        return hospital.getId() + ":" + lot.getId();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.project.mss.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.stock.StockAdjustmentDTO;
import com.project.mss.dto.stock.StockEntryDTO;
import com.project.mss.dto.stock.StockRowDTO;
import com.project.mss.dto.stock.StockSummaryDTO;
import com.project.mss.dto.stock.StockMovementDTO;
import com.project.mss.dto.stock.LotBalanceDTO;
import com.project.mss.dto.stock.LotStockDTO;
import com.project.mss.dto.material.MaterialDTO;
import com.project.mss.dto.stock.MaterialStockDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.Stock;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.MinimumStock;
import com.project.mss.model.entity.ProductSection;
import com.project.mss.model.entity.StockMovement;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.MovementType;
import com.project.mss.repository.StockRepository;
import com.project.mss.repository.StockMovementRepository;
import com.project.mss.repository.MinimumStockRepository;

/**
 * Single entry point for balance changes. Every change goes through debit/credit and
 * writes a stock_movement record, keeping the history auditable.
 */
@Service
public class StockService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;
    private final MinimumStockRepository minimumStockRepository;

    public StockService(StockRepository stockRepository, StockMovementRepository stockMovementRepository,
                          MaterialService materialService, AccessControlService accessControlService,
                          MinimumStockRepository minimumStockRepository) {
        this.stockRepository = stockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.materialService = materialService;
        this.accessControlService = accessControlService;
        this.minimumStockRepository = minimumStockRepository;
    }

    // ============================================================ base operations

    public int balance(Lot lot, Hospital hospital, Location location) {
        return stockRepository.lock(lot.getId(), hospital.getId(), location)
                .map(Stock::getQuantity).orElse(0);
    }

    @Transactional
    public void debit(Lot lot, Hospital hospital, Location location, int quantity) {
        Stock e = stockRepository.lock(lot.getId(), hospital.getId(), location)
                .orElseThrow(() -> noBalance(lot, hospital, location, 0, quantity));
        if (e.getQuantity() < quantity) {
            throw noBalance(lot, hospital, location, e.getQuantity(), quantity);
        }
        e.setQuantity(e.getQuantity() - quantity);
        stockRepository.save(e);
    }

    @Transactional
    public void credit(Lot lot, Hospital hospital, Location location, int quantity) {
        requireValidLocation(hospital, location);
        Stock e = stockRepository.lock(lot.getId(), hospital.getId(), location)
                .orElseGet(() -> new Stock(lot, hospital, location));
        e.setQuantity(e.getQuantity() + quantity);
        stockRepository.save(e);
    }

    /** Transfers between (hospital, location) pairs and records the movement. */
    @Transactional
    public StockMovement transfer(MovementType type, Lot lot, int quantity,
                                   Hospital source, Location sourceLocation,
                                   Hospital destination, Location destinationLocation,
                                   Long loanId, Long deliveryId, String notes) {
        debit(lot, source, sourceLocation, quantity);
        credit(lot, destination, destinationLocation, quantity);
        StockMovement m = newMovement(type, lot, quantity);
        m.setSourceHospital(source);
        m.setSourceLocation(sourceLocation);
        m.setDestinationHospital(destination);
        m.setDestinationLocation(destinationLocation);
        m.setLoanId(loanId);
        m.setDeliveryId(deliveryId);
        m.setNotes(notes);
        return stockMovementRepository.save(m);
    }

    @Transactional
    public StockMovement recordSurgeryWithdrawal(Lot lot, int quantity, Hospital hospital, Long surgeryId) {
        debit(lot, hospital, Location.HOSPITAL, quantity);
        StockMovement m = newMovement(MovementType.SURGERY_WITHDRAWAL, lot, quantity);
        m.setSourceHospital(hospital);
        m.setSourceLocation(Location.HOSPITAL);
        m.setSurgeryId(surgeryId);
        return stockMovementRepository.save(m);
    }

    @Transactional
    public StockMovement reverseSurgeryWithdrawal(Lot lot, int quantity, Hospital hospital, Long surgeryId,
                                              String reason) {
        credit(lot, hospital, Location.HOSPITAL, quantity);
        StockMovement m = newMovement(MovementType.SURGERY_REVERSAL, lot, quantity);
        m.setDestinationHospital(hospital);
        m.setDestinationLocation(Location.HOSPITAL);
        m.setSurgeryId(surgeryId);
        m.setNotes(reason);
        return stockMovementRepository.save(m);
    }

    @Transactional
    public StockMovement recordEntry(Lot lot, int quantity, Hospital hospital, Location location, String notes) {
        credit(lot, hospital, location, quantity);
        StockMovement m = newMovement(MovementType.ENTRY, lot, quantity);
        m.setDestinationHospital(hospital);
        m.setDestinationLocation(location);
        m.setNotes(notes);
        return stockMovementRepository.save(m);
    }

    /**
     * Material received from the supplier (stock entry): enters the storeroom assigned to the hospital.
     * The movement carries the receipt date informed in the entry.
     */
    @Transactional
    public StockMovement recordStockEntry(Lot lot, int quantity, Hospital hospital, Long stockEntryId,
                                          LocalDateTime date) {
        credit(lot, hospital, Location.STOREROOM, quantity);
        StockMovement m = newMovement(MovementType.ENTRY, lot, quantity);
        m.setDestinationHospital(hospital);
        m.setDestinationLocation(Location.STOREROOM);
        m.setStockEntryId(stockEntryId);
        m.setCreatedAt(date);
        m.setNotes("Stock entry #" + stockEntryId);
        return stockMovementRepository.save(m);
    }

    /**
     * Correction of a stock entry already recorded: difference > 0 adds to the storeroom, < 0 removes from it.
     * Recorded as ENTRY_CORRECTION with the signed difference, linked to the entry.
     */
    @Transactional
    public StockMovement correctStockEntry(Lot lot, int difference, Hospital hospital, Long stockEntryId) {
        if (difference < 0) {
            debit(lot, hospital, Location.STOREROOM, -difference);
        } else {
            credit(lot, hospital, Location.STOREROOM, difference);
        }
        StockMovement m = newMovement(MovementType.ENTRY_CORRECTION, lot, difference);
        if (difference > 0) {
            m.setDestinationHospital(hospital);
            m.setDestinationLocation(Location.STOREROOM);
        } else {
            m.setSourceHospital(hospital);
            m.setSourceLocation(Location.STOREROOM);
        }
        m.setStockEntryId(stockEntryId);
        m.setNotes("Correction of stock entry #" + stockEntryId);
        return stockMovementRepository.save(m);
    }

    /** Sets the absolute balance (inventory). Records the difference as INVENTORY_ADJUSTMENT. */
    @Transactional
    public void adjustBalance(Lot lot, Hospital hospital, Location location, int countedQuantity,
                             String reason) {
        requireValidLocation(hospital, location);
        Stock e = stockRepository.lock(lot.getId(), hospital.getId(), location)
                .orElseGet(() -> new Stock(lot, hospital, location));
        int difference = countedQuantity - e.getQuantity();
        if (difference == 0) return;
        e.setQuantity(countedQuantity);
        stockRepository.save(e);

        StockMovement m = newMovement(MovementType.INVENTORY_ADJUSTMENT, lot, difference);
        if (difference > 0) {
            m.setDestinationHospital(hospital);
            m.setDestinationLocation(location);
        } else {
            m.setSourceHospital(hospital);
            m.setSourceLocation(location);
        }
        m.setNotes(reason);
        stockMovementRepository.save(m);
    }

    /** A distribution center only keeps material in the storeroom. */
    public static void requireValidLocation(Hospital hospital, Location location) {
        if (hospital.isDistributionCenter() && location == Location.HOSPITAL) {
            throw new BusinessRuleException(hospital.getName()
                    + " is a distribution center: its material can only be in the storeroom");
        }
    }

    // ============================================================ use cases

    @Transactional
    public void manualEntry(StockEntryDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());
        Material material = materialService.findByRef(dto.ref());
        Lot lot = materialService.getOrCreateLot(material, dto.lot(), dto.expiryDate());
        recordEntry(lot, dto.quantity(), hospital, dto.location(), "Manual entry");
    }

    @Transactional
    public void manualAdjustment(StockAdjustmentDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());
        Lot lot = materialService.findLot(dto.lotId());
        adjustBalance(lot, hospital, dto.location(), dto.countedQuantity(), dto.reason());
    }

    /**
     * Hospital stock view in spreadsheet layout: one row per lot,
     * with the balance inside the hospital and the storeroom balance assigned to it.
     */
    @Transactional(readOnly = true)
    public List<StockRowDTO> hospitalView(Long hospitalId, boolean includeExpired) {
        accessControlService.requireLotAccess();
        accessControlService.requireHospitalAccess(hospitalId);
        LocalDate today = LocalDate.now();
        Map<Long, int[]> balances = new LinkedHashMap<>();
        Map<Long, Lot> lots = new LinkedHashMap<>();
        for (Stock e : stockRepository.listByHospital(hospitalId)) {
            Lot l = e.getLot();
            if (!includeExpired && l.isExpired(today)) continue;
            lots.putIfAbsent(l.getId(), l);
            int[] s = balances.computeIfAbsent(l.getId(), k -> new int[2]);
            if (e.getLocation() == Location.HOSPITAL) s[0] += e.getQuantity();
            else s[1] += e.getQuantity();
        }
        List<StockRowDTO> rows = new ArrayList<>();
        lots.forEach((id, l) -> {
            Material m = l.getMaterial();
            int[] s = balances.get(id);
            rows.add(new StockRowDTO(m.getId(), m.getRef(), m.getDescription(),
                    m.getProductLines().stream().sorted().toList(), m.getComponent(),
                    m.getSize(), m.getColor(), l.getId(), l.getNumber(), l.getExpiryDate(), l.isExpired(today),
                    s[0], s[1]));
        });
        return rows;
    }

    /**
     * Stock of every material matching the term (REF, name or description, partial match),
     * per hospital and per lot, restricted to the hospitals the user can see.
     * With hospitalId, only that hospital is returned. Totals ignore expired lots.
     */
    @Transactional(readOnly = true)
    public Page<MaterialStockDTO> materialStock(String term, Long hospitalId, boolean includeExpired,
                                                Pageable pageable) {
        accessControlService.requireLotAccess();
        Page<MaterialDTO> page = materialService.find(term, pageable);
        List<MaterialDTO> materials = page.getContent();
        if (materials.isEmpty()) return new PageImpl<>(List.of(), pageable, page.getTotalElements());

        var allowed = hospitalId != null
                ? java.util.Set.of(accessControlService.requireHospitalAccess(hospitalId).getId())
                : accessControlService.allowedHospitals();
        LocalDate today = LocalDate.now();

        // materialId -> hospitalId -> lotId -> {hospital, storeroom}
        Map<Long, Map<Long, Map<Long, int[]>>> byMaterial = new LinkedHashMap<>();
        Map<Long, Hospital> hospitals = new LinkedHashMap<>();
        Map<Long, Lot> lots = new LinkedHashMap<>();
        List<Long> ids = materials.stream().map(MaterialDTO::id).toList();
        for (Stock e : stockRepository.listByMaterials(ids)) {
            Long hId = e.getHospital().getId();
            if (!allowed.contains(hId)) continue;
            Lot l = e.getLot();
            if (!includeExpired && l.isExpired(today)) continue;
            hospitals.putIfAbsent(hId, e.getHospital());
            lots.putIfAbsent(l.getId(), l);
            int[] q = byMaterial.computeIfAbsent(l.getMaterial().getId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(hId, k -> new LinkedHashMap<>())
                    .computeIfAbsent(l.getId(), k -> new int[2]);
            if (e.getLocation() == Location.HOSPITAL) q[0] += e.getQuantity();
            else q[1] += e.getQuantity();
        }

        List<MaterialStockDTO> result = new ArrayList<>();
        for (MaterialDTO m : materials) {
            List<MaterialStockDTO.HospitalStock> hospitalRows = new ArrayList<>();
            int totalHospital = 0, totalStoreroom = 0;
            for (var entry : byMaterial.getOrDefault(m.id(), Map.of()).entrySet()) {
                Hospital h = hospitals.get(entry.getKey());
                List<MaterialStockDTO.LotStock> lotRows = new ArrayList<>();
                int hq = 0, sq = 0;
                for (var le : entry.getValue().entrySet()) {
                    Lot l = lots.get(le.getKey());
                    int[] q = le.getValue();
                    boolean expired = l.isExpired(today);
                    lotRows.add(new MaterialStockDTO.LotStock(l.getId(), l.getNumber(), l.getExpiryDate(), expired, q[0], q[1]));
                    if (!expired) { hq += q[0]; sq += q[1]; }
                }
                hospitalRows.add(new MaterialStockDTO.HospitalStock(h.getId(), h.getName(), hq, sq, hq + sq, lotRows));
                totalHospital += hq;
                totalStoreroom += sq;
            }
            result.add(new MaterialStockDTO(m.id(), m.ref(), m.productLines(), m.component(), m.description(), m.size(), m.color(),
                    totalHospital, totalStoreroom, totalHospital + totalStoreroom, hospitalRows));
        }
        return new PageImpl<>(result, pageable, page.getTotalElements());
    }

    /**
     * Administrator's stock tab "by lot": one row per lot and hospital, for one hospital or every hospital
     * (hospitalId empty). term searches lot number, REF, name, description and GTIN, so a lot can be located
     * across every hospital.
     */
    @Transactional(readOnly = true)
    public Page<LotStockDTO> searchLots(String term, Long hospitalId, Pageable pageable) {
        accessControlService.requireLotAccess();
        java.util.Set<Long> hospitals = hospitalId != null
                ? java.util.Set.of(accessControlService.requireHospitalAccess(hospitalId).getId())
                : accessControlService.allowedHospitals();
        if (hospitals.isEmpty()) return Page.empty(pageable);
        String t = term == null || term.isBlank() ? null : term.trim();
        return stockRepository.searchLots(hospitals, t, pageable);
    }

    @Transactional(readOnly = true)
    public List<LotBalanceDTO> findLotLocations(Long lotId) {
        accessControlService.requireLotAccess();
        var allowed = accessControlService.allowedHospitals();
        return stockRepository.listByLot(lotId).stream()
                .filter(e -> allowed.contains(e.getHospital().getId()))
                .map(e -> new LotBalanceDTO(e.getHospital().getId(), e.getHospital().getName(), e.getLocation(),
                        e.getQuantity()))
                .toList();
    }

    /**
     * Surgical tech's stock view: per material, the quantity inside the hospital (storeroom excluded),
     * ignoring expired lots, without lot details. Shows the REFs with an ideal greater than zero in the hospital
     * (quantity 0 when there is no balance) and any REF with balance. The screen groups by section.
     */
    @Transactional(readOnly = true)
    public List<StockSummaryDTO> hospitalSummary(Long hospitalId) {
        accessControlService.requireHospitalAccess(hospitalId);
        LocalDate today = LocalDate.now();
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        Map<Long, Material> materials = new LinkedHashMap<>();
        // REFs the hospital works with: ideal greater than zero (shown even without balance)
        for (MinimumStock ms : minimumStockRepository.listByHospital(hospitalId)) {
            Material m = ms.getMaterial();
            boolean hasIdeal = positive(ms.getHospitalIdeal()) || positive(ms.getIdealTotal());
            if (!hasIdeal || !Boolean.TRUE.equals(m.getActive())) continue;
            materials.putIfAbsent(m.getId(), m);
            quantities.putIfAbsent(m.getId(), 0);
        }
        // Balance inside the hospital (valid lots); a REF with balance is shown even without an ideal
        for (Stock e : stockRepository.listByHospital(hospitalId)) {
            if (e.getLocation() != Location.HOSPITAL || e.getQuantity() <= 0 || e.getLot().isExpired(today)) continue;
            Material m = e.getLot().getMaterial();
            materials.putIfAbsent(m.getId(), m);
            quantities.merge(m.getId(), e.getQuantity(), Integer::sum);
        }
        return materials.values().stream()
                .sorted(java.util.Comparator.comparing(Material::getRef))
                .map(m -> {
                    ProductSection s = m.getSection();
                    return new StockSummaryDTO(m.getId(), m.getRef(), m.getComponent(), m.getDescription(), m.getSize(),
                            m.getColor(), m.getProductLines().stream().sorted().toList(), quantities.get(m.getId()),
                            s == null ? null : s.getId(), s == null ? null : s.getName(),
                            s == null ? null : s.getDisplayOrder());
                })
                .toList();
    }

    /** Lots of a material inside the hospital, valid on the date and with balance, earliest expiry first. */
    @Transactional(readOnly = true)
    public List<Stock> lotsInsideHospital(Hospital hospital, Long materialId, LocalDate date) {
        return stockRepository.listByHospital(hospital.getId()).stream()
                .filter(e -> e.getLocation() == Location.HOSPITAL && e.getQuantity() > 0
                        && e.getLot().getMaterial().getId().equals(materialId) && !e.getLot().isExpired(date))
                .sorted(java.util.Comparator.comparing((Stock e) -> e.getLot().getExpiryDate()))
                .toList();
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDTO> history(Long hospitalId, LocalDate start, LocalDate end, Pageable pageable) {
        accessControlService.requireLotAccess();
        accessControlService.requireHospitalAccess(hospitalId);
        LocalDateTime i = (start != null ? start : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime f = (end != null ? end : LocalDate.now()).plusDays(1).atStartOfDay().minusNanos(1);
        return stockMovementRepository.listByHospital(hospitalId, i, f, pageable).map(StockMovementDTO::of);
    }

    // ============================================================ helpers

    private StockMovement newMovement(MovementType type, Lot lot, int quantity) {
        StockMovement m = new StockMovement();
        m.setType(type);
        m.setLot(lot);
        m.setQuantity(quantity);
        try {
            m.setUser(accessControlService.currentUser());
        } catch (RuntimeException withoutUser) {
            m.setUser(null); // automated processes
        }
        return m;
    }

    private BusinessRuleException noBalance(Lot lot, Hospital hospital, Location loc, int available, int requested) {
        String where = loc == Location.STOREROOM ? "in the storeroom (assigned to " + hospital.getName() + ")"
                                              : "at " + hospital.getName();
        return new BusinessRuleException(String.format(
                "Insufficient balance for lot %s (REF %s) %s: available %d, requested %d",
                lot.getNumber(), lot.getMaterial().getRef(), where, available, requested));
    }
}

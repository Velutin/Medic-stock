package com.project.mss.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.hospital.PriceDTO;
import com.project.mss.dto.material.LotDTO;
import com.project.mss.dto.material.MaterialDTO;
import com.project.mss.dto.material.MaterialFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.HospitalPrice;
import com.project.mss.repository.LotRepository;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.HospitalPriceRepository;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.SurgeryItemRepository;

@Service
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final LotRepository lotRepository;
    private final HospitalPriceRepository hospitalPriceRepository;
    private final HospitalRepository hospitalRepository;
    private final SurgeryItemRepository surgeryItemRepository;
    private final AccessControlService accessControlService;

    public MaterialService(MaterialRepository materialRepository, LotRepository lotRepository,
                           HospitalPriceRepository hospitalPriceRepository, HospitalRepository hospitalRepository,
                           SurgeryItemRepository surgeryItemRepository, AccessControlService accessControlService) {
        this.materialRepository = materialRepository;
        this.lotRepository = lotRepository;
        this.hospitalPriceRepository = hospitalPriceRepository;
        this.hospitalRepository = hospitalRepository;
        this.surgeryItemRepository = surgeryItemRepository;
        this.accessControlService = accessControlService;
    }

    // ---------------------------------------------------------------- catalog

    @Transactional(readOnly = true)
    public Page<MaterialDTO> find(String term, Pageable pageable) {
        String t = (term == null || term.isBlank()) ? null : term.trim();
        return materialRepository.find(t, pageable).map(MaterialDTO::of);
    }

    @Transactional
    public MaterialDTO create(MaterialFormDTO dto) {
        String ref = normalizeRef(dto.ref());
        if (materialRepository.findByRefIgnoreCase(ref).isPresent()) {
            throw new BusinessRuleException("REF " + ref + " already registered");
        }
        Material m = new Material();
        apply(m, dto);
        return MaterialDTO.of(materialRepository.save(m));
    }

    @Transactional
    public MaterialDTO update(Long id, MaterialFormDTO dto) {
        Material m = materialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Material " + id + " not found"));
        String ref = normalizeRef(dto.ref());
        materialRepository.findByRefIgnoreCase(ref)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new BusinessRuleException("REF " + ref + " already belongs to another material"); });
        apply(m, dto);
        return MaterialDTO.of(materialRepository.save(m));
    }

    public Material findByRef(String ref) {
        return materialRepository.findByRefIgnoreCase(normalizeRef(ref))
                .orElseThrow(() -> new EntityNotFoundException("REF " + ref + " is not registered"));
    }

    /** Finds the REF or registers it with the given description (used by imports). */
    @Transactional
    public Material getOrCreate(String ref, String description) {
        String r = normalizeRef(ref);
        return materialRepository.findByRefIgnoreCase(r).orElseGet(() -> {
            Material m = new Material();
            m.setRef(r);
            m.setDescription(description == null || description.isBlank() ? r : description.trim());
            return materialRepository.save(m);
        });
    }

    // ------------------------------------------------------------------- lots

    /**
     * Returns the lot identified by material + number + expiry date, creating it when needed.
     * The same number with another expiry date is a different lot (units sterilized on different days).
     * Validation errors do not mark the caller's transaction for rollback, so a spreadsheet
     * import can report the row and continue.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public Lot getOrCreateLot(Material material, String number, LocalDate expiryDate) {
        if (expiryDate == null) {
            throw new BusinessRuleException("Expiry date is required for lot " + number + " (REF " + material.getRef() + ")");
        }
        String n = number.trim().toUpperCase();
        Optional<Lot> existing = lotRepository.findByMaterialIdAndNumberIgnoreCaseAndExpiryDate(material.getId(), n, expiryDate);
        if (existing.isPresent()) {
            return existing.get();
        }
        Lot l = new Lot();
        l.setMaterial(material);
        l.setNumber(n);
        l.setExpiryDate(expiryDate);
        return lotRepository.save(l);
    }

    @Transactional(readOnly = true)
    public List<LotDTO> findLots(String number) {
        accessControlService.requireLotAccess();
        return lotRepository.findByNumber(number.trim()).stream().map(LotDTO::of).toList();
    }

    public Lot findLot(Long id) {
        return lotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lot " + id + " not found"));
    }

    // ------------------------------------------------------------------ prices

    /**
     * Material value in the hospital price table (SIGTAP or tender). Hospitals supplied by a
     * distribution center without their own value use the center's table.
     */
    public Optional<BigDecimal> hospitalValue(Long hospitalId, Long materialId) {
        return hospitalPriceRepository.findByHospitalIdAndMaterialId(hospitalId, materialId)
                .or(() -> hospitalRepository.findCenterOf(hospitalId)
                        .flatMap(center -> hospitalPriceRepository.findByHospitalIdAndMaterialId(center.getId(), materialId)))
                .map(HospitalPrice::getValue);
    }

    /** Full table replacement: removes the hospital values of the materials not in keepMaterialIds. */
    @Transactional
    public int removePricesExcept(Hospital hospital, java.util.Collection<Long> keepMaterialIds) {
        return hospitalPriceRepository.deleteByHospitalExcept(hospital.getId(), keepMaterialIds);
    }

    public void setPrice(Hospital hospital, Material material, BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new BusinessRuleException("Invalid value for REF " + material.getRef());
        }
        HospitalPrice p = hospitalPriceRepository.findByHospitalIdAndMaterialId(hospital.getId(), material.getId())
                .orElseGet(() -> {
                    HospitalPrice created = new HospitalPrice();
                    created.setHospital(hospital);
                    created.setMaterial(material);
                    return created;
                });
        p.setValue(value);
        hospitalPriceRepository.save(p);
        fillMissingPrices(hospital, material, value);
    }

    /** Sets one value of the hospital table (ADMIN). */
    @Transactional
    public PriceDTO putPrice(Long hospitalId, Long materialId, BigDecimal value) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new EntityNotFoundException("Material " + materialId + " not found"));
        setPrice(hospital, material, value);
        return PriceDTO.of(hospitalPriceRepository.findByHospitalIdAndMaterialId(hospitalId, materialId).orElseThrow());
    }

    /**
     * Surgery items recorded while the REF had no value receive the new value, and their surgery total
     * is recalculated. Items that already had a value are never changed (audit: past surgeries keep the
     * value of the day). A value registered in a distribution center also fills the items of the hospitals
     * it supplies that have no value of their own.
     */
    private void fillMissingPrices(Hospital hospital, Material material, BigDecimal value) {
        java.util.Set<Long> hospitalIds = new java.util.HashSet<>();
        hospitalIds.add(hospital.getId());
        if (hospital.isDistributionCenter()) {
            hospital.getCoveredHospitals().stream()
                    .filter(h -> hospitalPriceRepository.findByHospitalIdAndMaterialId(h.getId(), material.getId()).isEmpty())
                    .forEach(h -> hospitalIds.add(h.getId()));
        }
        var surgeries = new java.util.HashSet<com.project.mss.model.entity.Surgery>();
        for (var item : surgeryItemRepository.listWithoutPrice(hospitalIds, material.getId())) {
            item.setUnitValue(value);
            surgeries.add(item.getSurgery());
        }
        surgeries.forEach(com.project.mss.model.entity.Surgery::recalculateTotal);
    }

    /**
     * Effective price table of a hospital: its own values plus, for a hospital supplied by a
     * distribution center, the center's values for the REFs it has no value of its own.
     * Each line tells whose table it comes from. This is the same rule used at surgery withdrawal.
     */
    @Transactional(readOnly = true)
    public List<PriceDTO> hospitalTable(Long hospitalId) {
        accessControlService.requireManager();
        accessControlService.requireHospitalAccess(hospitalId);
        Map<Long, PriceDTO> byMaterial = new LinkedHashMap<>();
        hospitalPriceRepository.listByHospital(hospitalId)
                .forEach(p -> byMaterial.put(p.getMaterial().getId(), PriceDTO.of(p)));
        hospitalRepository.findCenterOf(hospitalId).ifPresent(center ->
                hospitalPriceRepository.listByHospital(center.getId())
                        .forEach(p -> byMaterial.putIfAbsent(p.getMaterial().getId(), PriceDTO.of(p))));
        return byMaterial.values().stream()
                .sorted(java.util.Comparator.comparing(PriceDTO::ref))
                .toList();
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Assigns a GTIN to the material if it has none yet. Used by imports and by the scanner,
     * which learns the GTIN the first time a lot is identified unambiguously.
     */
    @Transactional
    public void assignGtinIfMissing(Material material, String gtin) {
        String g = normalizeGtin(gtin);
        if (g == null || material.getGtin() != null) return;
        if (materialRepository.findByGtin(g).isPresent()) return;
        material.setGtin(g);
        materialRepository.save(material);
    }

    /** Left-pads GTIN-8/12/13 to 14 digits. Returns null for blank or non-GTIN values. */
    public static String normalizeGtin(String gtin) {
        if (gtin == null) return null;
        String g = gtin.replaceAll("\\D", "");
        if (!(g.length() == 8 || (g.length() >= 12 && g.length() <= 14))) return null;
        return "0".repeat(14 - g.length()) + g;
    }

    public static String normalizeRef(String ref) {
        if (ref == null || ref.isBlank()) throw new BusinessRuleException("REF not provided");
        return ref.trim().toUpperCase();
    }

    private void apply(Material m, MaterialFormDTO dto) {
        m.setRef(normalizeRef(dto.ref()));
        m.setDescription(dto.description().trim());
        String gtin = normalizeGtin(dto.gtin());
        if (gtin != null) {
            materialRepository.findByGtin(gtin)
                    .filter(other -> !other.getId().equals(m.getId()))
                    .ifPresent(other -> { throw new BusinessRuleException("GTIN " + gtin + " already belongs to REF " + other.getRef()); });
        }
        m.setGtin(gtin);
        m.getProductLines().clear();
        m.getProductLines().addAll(dto.productLines());
        m.setComponent(dto.component());
        m.setSize(dto.size());
        m.setColor(dto.color() == null || dto.color().isBlank() ? null : dto.color().toUpperCase());
        if (dto.active() != null) m.setActive(dto.active());
    }
}

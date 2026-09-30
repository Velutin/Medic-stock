package com.project.mss.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

@Service
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final LotRepository lotRepository;
    private final HospitalPriceRepository hospitalPriceRepository;
    private final AccessControlService accessControlService;

    public MaterialService(MaterialRepository materialRepository, LotRepository lotRepository,
                           HospitalPriceRepository hospitalPriceRepository, AccessControlService accessControlService) {
        this.materialRepository = materialRepository;
        this.lotRepository = lotRepository;
        this.hospitalPriceRepository = hospitalPriceRepository;
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
     * Returns the lot, creating it when needed. The expiry date is mandatory and must match
     * the registered one: a different date usually means a typo or a mislabeled item.
     * Validation errors do not mark the caller's transaction for rollback, so a spreadsheet
     * import can report the row and continue.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public Lot getOrCreateLot(Material material, String number, LocalDate expiryDate) {
        if (expiryDate == null) {
            throw new BusinessRuleException("Expiry date is required for lot " + number + " (REF " + material.getRef() + ")");
        }
        String n = number.trim().toUpperCase();
        Optional<Lot> existing = lotRepository.findByMaterialIdAndNumberIgnoreCase(material.getId(), n);
        if (existing.isPresent()) {
            Lot l = existing.get();
            if (!l.getExpiryDate().equals(expiryDate)) {
                throw new BusinessRuleException("Lot " + n + " (REF " + material.getRef() + ") is registered with expiry date "
                        + l.getExpiryDate() + ", not " + expiryDate);
            }
            return l;
        }
        Lot l = new Lot();
        l.setMaterial(material);
        l.setNumber(n);
        l.setExpiryDate(expiryDate);
        return lotRepository.save(l);
    }

    @Transactional(readOnly = true)
    public List<LotDTO> findLots(String number) {
        return lotRepository.findByNumber(number.trim()).stream().map(LotDTO::of).toList();
    }

    public Lot findLot(Long id) {
        return lotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lot " + id + " not found"));
    }

    // ------------------------------------------------------------------ prices

    /** Material value in the hospital price table (SIGTAP or tender). */
    public Optional<BigDecimal> hospitalValue(Long hospitalId, Long materialId) {
        return hospitalPriceRepository.findByHospitalIdAndMaterialId(hospitalId, materialId).map(HospitalPrice::getValue);
    }

    @Transactional
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
    }

    @Transactional(readOnly = true)
    public List<PriceDTO> hospitalTable(Long hospitalId) {
        accessControlService.isHospitalAllowed(hospitalId);
        return hospitalPriceRepository.listByHospital(hospitalId).stream().map(PriceDTO::of).toList();
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
        m.setComponent(dto.component());
        m.setSize(dto.size());
        m.setColor(dto.color() == null || dto.color().isBlank() ? null : dto.color().toUpperCase());
        if (dto.active() != null) m.setActive(dto.active());
    }
}

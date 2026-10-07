package com.project.mss.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.entry.LotRefConflictDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.repository.LotRepository;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.StockRepository;
import com.project.mss.repository.SurgeryItemRepository;

/**
 * Rule of the entry: a lot number belongs to only one REF (one REF may have the number with several expiry dates).
 * When a number already registered with another REF enters, the user confirms the REF change of those lots: their
 * balances and history move to the new REF. Lots already withdrawn in a surgery are never changed.
 */
@Service
public class LotRefChangeService {

    private final LotRepository lotRepository;
    private final MaterialRepository materialRepository;
    private final StockRepository stockRepository;
    private final SurgeryItemRepository surgeryItemRepository;
    private final AccessControlService accessControlService;

    public LotRefChangeService(LotRepository lotRepository, MaterialRepository materialRepository,
                               StockRepository stockRepository, SurgeryItemRepository surgeryItemRepository,
                               AccessControlService accessControlService) {
        this.lotRepository = lotRepository;
        this.materialRepository = materialRepository;
        this.stockRepository = stockRepository;
        this.surgeryItemRepository = surgeryItemRepository;
        this.accessControlService = accessControlService;
    }

    /** For each material + number, the lots with the same number and another REF; only the checks with conflicts. */
    @Transactional(readOnly = true)
    public List<LotRefConflictDTO> check(List<LotRefConflictDTO.Check> checks) {
        accessControlService.requireManager();
        Map<String, LotRefConflictDTO> result = new LinkedHashMap<>();
        for (LotRefConflictDTO.Check c : checks) {
            if (c == null || c.materialId() == null || c.lot() == null || c.lot().isBlank()) continue;
            String number = c.lot().trim().toUpperCase();
            String key = c.materialId() + ":" + number;
            if (result.containsKey(key)) continue;
            List<Lot> others = lotRepository.findByNumberOfOtherMaterials(number, c.materialId());
            if (!others.isEmpty()) {
                result.put(key, new LotRefConflictDTO(c.materialId(), number, others.stream().map(this::describe).toList()));
            }
        }
        return new ArrayList<>(result.values());
    }

    /** Lots with this number registered with another REF (empty: nothing to change). */
    @Transactional(readOnly = true)
    public List<Lot> conflicts(Long materialId, String number) {
        return lotRepository.findByNumberOfOtherMaterials(number.trim().toUpperCase(), materialId);
    }

    /**
     * Every lot with this number and another REF becomes the given material. A lot whose new identity
     * (material + number + expiry date) already exists is merged into it (balances summed, history moved).
     * Refused, changing nothing, when one of them was withdrawn in a surgery.
     * After a merge the persistence context is cleared: callers reload what they use afterwards.
     */
    @Transactional
    public void changeRef(Long materialId, String number) {
        Material target = materialRepository.findById(materialId)
                .orElseThrow(() -> new EntityNotFoundException("Material " + materialId + " not found"));
        List<Lot> others = conflicts(materialId, number);
        for (Lot lot : others) {
            if (surgeryItemRepository.usedInSurgery(lot.getId())) {
                throw new BusinessRuleException(String.format(
                        "Lot %s (REF %s) was already used in a surgery and its REF cannot be changed: check the REF of the item",
                        lot.getNumber(), lot.getMaterial().getRef()));
            }
        }
        List<Long[]> merges = new ArrayList<>();
        for (Lot lot : others) {
            Optional<Lot> same = lotRepository.findByMaterialIdAndNumberIgnoreCaseAndExpiryDate(
                    target.getId(), lot.getNumber(), lot.getExpiryDate());
            if (same.isPresent()) {
                merges.add(new Long[]{lot.getId(), same.get().getId()});
            } else {
                lot.setMaterial(target);
                lotRepository.saveAndFlush(lot);
            }
        }
        for (Long[] m : merges) {
            lotRepository.mergeStockBalances(m[0], m[1]);
            lotRepository.deleteMergedStock(m[0], m[1]);
            lotRepository.mergeEntryItems(m[0], m[1]);
            lotRepository.deleteMergedEntryItems(m[0], m[1]);
            lotRepository.moveReferences(m[0], m[1]);
            lotRepository.deleteMerged(m[0]);
        }
    }

    private LotRefConflictDTO.ExistingLot describe(Lot lot) {
        List<LotRefConflictDTO.Balance> balances = stockRepository.listByLot(lot.getId()).stream()
                .map(s -> new LotRefConflictDTO.Balance(s.getHospital().getName(), s.getLocation(), s.getQuantity()))
                .toList();
        List<LotRefConflictDTO.SurgeryUse> surgeries = surgeryUses(lot);
        Material m = lot.getMaterial();
        return new LotRefConflictDTO.ExistingLot(lot.getId(), m.getId(), m.getRef(), m.getDescription(),
                lot.getExpiryDate(), balances.stream().mapToInt(LotRefConflictDTO.Balance::quantity).sum(),
                !surgeries.isEmpty(), balances, surgeries);
    }

    /**
     * Surgeries (not cancelled) where this lot was withdrawn, newest first. A lot can appear in more than one
     * item of the same surgery, so the quantities are added up per surgery. This is also what tells whether the
     * lot can change REF: a lot used in a surgery never changes.
     */
    private List<LotRefConflictDTO.SurgeryUse> surgeryUses(Lot lot) {
        Map<Long, LotRefConflictDTO.SurgeryUse> bySurgery = new LinkedHashMap<>();
        for (var item : surgeryItemRepository.listSurgeryUses(lot.getId())) {
            var surgery = item.getSurgery();
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            bySurgery.merge(surgery.getId(),
                    new LotRefConflictDTO.SurgeryUse(surgery.getId(), surgery.getSurgeryDate(),
                            surgery.getHospital().getName(), surgery.getPatientName(), surgery.getStatus().name(),
                            quantity, surgery.getSheetFile() != null),
                    (a, b) -> new LotRefConflictDTO.SurgeryUse(a.surgeryId(), a.surgeryDate(), a.hospital(),
                            a.patient(), a.status(), a.quantity() + b.quantity(), a.hasSheet()));
        }
        return List.copyOf(bySurgery.values());
    }
}

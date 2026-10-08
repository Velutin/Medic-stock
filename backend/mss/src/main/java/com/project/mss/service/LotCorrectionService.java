package com.project.mss.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.stock.LotCorrectionDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.repository.LotRepository;

/**
 * Correction of a lot typed wrong at the entry, made by an administrator from the stock by lot.
 *
 * A lot is a single record (material + number + expiry date) whose balances are spread over hospitals and
 * storerooms, so correcting the number or the date corrects it everywhere the lot appears - which is what a
 * typing error needs, because it is the same physical lot.
 *
 * Unlike the REF change of the entry, a lot that has already been withdrawn in a surgery can still be
 * corrected: otherwise a typo would be permanent. The history of those surgeries then shows the corrected
 * number, which is the real one; the screen warns before saving.
 */
@Service
public class LotCorrectionService {

    private final LotRepository lotRepository;
    private final MaterialService materialService;
    private final StockService stockService;
    private final AccessControlService accessControlService;

    public LotCorrectionService(LotRepository lotRepository, MaterialService materialService,
                                StockService stockService, AccessControlService accessControlService) {
        this.lotRepository = lotRepository;
        this.materialService = materialService;
        this.stockService = stockService;
        this.accessControlService = accessControlService;
    }

    /**
     * Applies the correction and, when countedQuantity is given, the balance of the line in one transaction.
     * When the corrected identity already exists in the same REF, the two lots are the same thing and are
     * merged: balances summed and history moved, as the REF change already does.
     */
    @Transactional
    public void correct(Long lotId, LotCorrectionDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());
        Lot lot = materialService.findLot(lotId);

        String number = dto.lot().trim().toUpperCase();
        LocalDate expiryDate = dto.expiryDate();
        String reason = dto.reason().trim();
        String before = describe(lot.getNumber(), lot.getExpiryDate());
        boolean changed = !number.equalsIgnoreCase(lot.getNumber()) || !expiryDate.equals(lot.getExpiryDate());

        Long survivingId = lot.getId();
        if (changed) {
            survivingId = applyIdentity(lot, number, expiryDate);
        }

        // After a merge the persistence context is cleared, so the lot is loaded again.
        Lot surviving = materialService.findLot(survivingId);
        if (changed) {
            stockService.recordLotCorrection(surviving, before, describe(number, expiryDate), reason);
        }
        if (dto.countedQuantity() != null) {
            stockService.adjustBalance(surviving, hospital, dto.location(), dto.countedQuantity(), reason);
        }
    }

    /**
     * Gives the lot its corrected number and date, merging it into the existing lot when that identity is
     * already registered in the same REF. Returns the id of the lot that remains.
     */
    private Long applyIdentity(Lot lot, String number, LocalDate expiryDate) {
        // A lot number belongs to a single REF: the entry is where a REF change is decided, not here.
        List<Lot> otherRefs = lotRepository.findByNumberOfOtherMaterials(number, lot.getMaterial().getId());
        if (!otherRefs.isEmpty()) {
            throw new BusinessRuleException("Lot " + number + " is already registered with REF "
                    + otherRefs.get(0).getMaterial().getRef());
        }

        Optional<Lot> existing = lotRepository
                .findByMaterialIdAndNumberIgnoreCaseAndExpiryDate(lot.getMaterial().getId(), number, expiryDate)
                .filter(other -> !other.getId().equals(lot.getId()));
        if (existing.isEmpty()) {
            lot.setNumber(number);
            lot.setExpiryDate(expiryDate);
            lotRepository.saveAndFlush(lot);
            return lot.getId();
        }

        Long target = existing.get().getId();
        lotRepository.mergeStockBalances(lot.getId(), target);
        lotRepository.deleteMergedStock(lot.getId(), target);
        lotRepository.mergeEntryItems(lot.getId(), target);
        lotRepository.deleteMergedEntryItems(lot.getId(), target);
        lotRepository.moveReferences(lot.getId(), target);
        lotRepository.deleteMerged(lot.getId());
        return target;
    }

    private static String describe(String number, LocalDate expiryDate) {
        return number + " (" + expiryDate + ")";
    }
}

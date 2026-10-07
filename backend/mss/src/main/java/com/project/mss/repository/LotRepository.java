package com.project.mss.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.Lot;

public interface LotRepository extends JpaRepository<Lot, Long> {

    /**
     * Lot numbers are compared ignoring case and leading zeros: a label read as 005706061 finds the lot registered as
     * 5706061 (and the other way around). See {@link Lot#comparableNumber(String)}.
     */
    String SAME_NUMBER = "TRIM(LEADING '0' FROM UPPER(TRIM(l.number))) = TRIM(LEADING '0' FROM UPPER(TRIM(:number)))";

    @Query("SELECT l FROM Lot l WHERE l.material.id = :materialId AND l.expiryDate = :expiryDate AND " + SAME_NUMBER + " ORDER BY l.id")
    List<Lot> findSameLot(@Param("materialId") Long materialId, @Param("number") String number,
                          @Param("expiryDate") LocalDate expiryDate);

    /**
     * A lot is identified by material + number + expiry date (units sterilized on different days). When the number
     * exists written with and without the leading zeros, the one written exactly as given is preferred.
     */
    default Optional<Lot> findByMaterialIdAndNumberIgnoreCaseAndExpiryDate(Long materialId, String number, LocalDate expiryDate) {
        List<Lot> same = findSameLot(materialId, number, expiryDate);
        return same.stream().filter(l -> l.getNumber().equalsIgnoreCase(number.trim())).findFirst()
                .or(() -> same.stream().findFirst());
    }

    /** Every expiry date registered for a lot number of a material. */
    @Query("SELECT l FROM Lot l WHERE l.material.id = :materialId AND " + SAME_NUMBER + " ORDER BY l.expiryDate, l.id")
    List<Lot> findByMaterialIdAndNumberIgnoreCaseOrderByExpiryDateAsc(@Param("materialId") Long materialId,
                                                                      @Param("number") String number);

    @Query("SELECT l FROM Lot l WHERE " + SAME_NUMBER)
    List<Lot> findByNumber(@Param("number") String number);

    /** Lots with this number registered for another material (rule: a lot number belongs to one REF). */
    @Query("SELECT l FROM Lot l WHERE " + SAME_NUMBER + " AND l.material.id <> :materialId ORDER BY l.material.ref, l.expiryDate")
    List<Lot> findByNumberOfOtherMaterials(@Param("number") String number, @Param("materialId") Long materialId);

    // ---- merge of a lot into another one (same number and expiry date, REF change): every reference is moved

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
           UPDATE stock t SET quantity = t.quantity + s.quantity, updated_at = CURRENT_TIMESTAMP
           FROM stock s
           WHERE s.lot_id = :source AND t.lot_id = :target AND t.hospital_id = s.hospital_id AND t.location = s.location
           """, nativeQuery = true)
    void mergeStockBalances(@Param("source") Long source, @Param("target") Long target);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
           DELETE FROM stock s
           WHERE s.lot_id = :source AND EXISTS (SELECT 1 FROM stock t WHERE t.lot_id = :target
                 AND t.hospital_id = s.hospital_id AND t.location = s.location)
           """, nativeQuery = true)
    void deleteMergedStock(@Param("source") Long source, @Param("target") Long target);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
           UPDATE stock_entry_item t SET quantity = t.quantity + s.quantity
           FROM stock_entry_item s
           WHERE s.lot_id = :source AND t.lot_id = :target AND t.stock_entry_id = s.stock_entry_id
           """, nativeQuery = true)
    void mergeEntryItems(@Param("source") Long source, @Param("target") Long target);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
           DELETE FROM stock_entry_item s
           WHERE s.lot_id = :source AND EXISTS (SELECT 1 FROM stock_entry_item t WHERE t.lot_id = :target
                 AND t.stock_entry_id = s.stock_entry_id)
           """, nativeQuery = true)
    void deleteMergedEntryItems(@Param("source") Long source, @Param("target") Long target);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
           WITH moved_stock AS (UPDATE stock SET lot_id = :target WHERE lot_id = :source),
                moved_entries AS (UPDATE stock_entry_item SET lot_id = :target WHERE lot_id = :source),
                moved_movements AS (UPDATE stock_movement SET lot_id = :target WHERE lot_id = :source),
                moved_loans AS (UPDATE loan_item SET lot_id = :target WHERE lot_id = :source),
                moved_deliveries AS (UPDATE delivery_item SET lot_id = :target WHERE lot_id = :source)
           UPDATE surgery_item SET lot_id = :target WHERE lot_id = :source
           """, nativeQuery = true)
    void moveReferences(@Param("source") Long source, @Param("target") Long target);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM lot WHERE id = :id", nativeQuery = true)
    void deleteMerged(@Param("id") Long id);
}

package com.project.mss.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.material.MaterialDTO;
import com.project.mss.dto.material.ScannedCodeDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.enums.Location;
import com.project.mss.repository.LotRepository;
import com.project.mss.repository.MaterialRepository;

/**
 * Parses what was scanned at withdrawal (QR code, barcode or typed lot number)
 * and finds the registered lot.
 *
 * The material is identified by the GTIN in AI (01), which is the same on every unit
 * of a REF; the lot comes from AI (10) and its expiry date from AI (17). A lot number can
 * exist with several expiry dates (units sterilized on different days), so a lot is identified by
 * material + number + expiry date. When the expiry date is not available (typed lot),
 * the registered dates are narrowed to the ones with balance inside the surgery hospital.
 * Supported formats:
 *  - GS1 with parentheses: (01)07891234567890(17)270131(10)ABC123;
 *  - raw GS1: 01078912345678901727013110ABC123 (variable fields separated by FNC1/GS);
 *  - labeled text: "REF: 123.456 LOTE: ABC123";
 *  - the lot number alone.
 */
@Service
public class LotScanService {

    private static final char GS = '\u001D';
    private static final Pattern GS1_PARENTHESES = Pattern.compile("\\((\\d{2,4})\\)([^(]+)");
    private static final Pattern LOT_LABEL = Pattern.compile("(?i)\\b(?:LOTE|LOT|LT)\\s*[:=#]?\\s*([A-Z0-9][A-Z0-9./-]*)");
    private static final Pattern REF_LABEL = Pattern.compile("(?i)\\b(?:REF|CODIGO|CÓDIGO|COD)\\s*[:=#]?\\s*([A-Z0-9][A-Z0-9./-]*)");

    public enum Status { FOUND, NOT_FOUND, AMBIGUOUS }

    public record Result(Status status, Lot lot, List<Lot> candidates, String parsedLot, String parsedRef) {
        static Result found(Lot l) {
            return new Result(Status.FOUND, l, List.of(l), l.getNumber(), l.getMaterial().getRef());
        }
    }

    private final LotRepository lotRepository;
    private final MaterialRepository materialRepository;
    private final MaterialService materialService;
    private final StockService stockService;

    public LotScanService(LotRepository lotRepository, MaterialRepository materialRepository,
                          MaterialService materialService, StockService stockService) {
        this.lotRepository = lotRepository;
        this.materialRepository = materialRepository;
        this.materialService = materialService;
        this.stockService = stockService;
    }

    /**
     * @param hospital hospital of the surgery; used to choose among several expiry dates of the same lot
     */
    @Transactional
    public Result resolve(String scannedCode, String enteredRef, Hospital hospital) {
        String code = scannedCode == null ? "" : scannedCode.trim();
        String ref = enteredRef == null || enteredRef.isBlank() ? null : enteredRef.trim().toUpperCase();

        Map<String, String> gs1 = parseGs1(code);
        String gtin = MaterialService.normalizeGtin(gs1.get("01"));
        String lotNumber = gs1.get("10");
        LocalDate expiryDate = parseExpiry(gs1.get("17"));

        // A bare GTIN (EAN/GTIN barcode without lot) identifies the material only
        if (gs1.isEmpty() && code.matches("\\d{8}|\\d{12,14}")) {
            Optional<Material> byGtin = materialRepository.findByGtin(MaterialService.normalizeGtin(code));
            if (byGtin.isPresent()) {
                throw new BusinessRuleException("The code identifies REF " + byGtin.get().getRef()
                        + " but has no lot number. Scan the lot barcode or type the lot number.");
            }
        }

        if (lotNumber == null) {
            Matcher ml = LOT_LABEL.matcher(code);
            lotNumber = ml.find() ? ml.group(1) : (gs1.isEmpty() ? code : null);
            Matcher mr = REF_LABEL.matcher(code);
            if (ref == null && mr.find()) ref = mr.group(1).toUpperCase();
        }
        if (lotNumber == null || lotNumber.isBlank()) {
            Optional<Material> m = gtin == null ? Optional.empty() : materialRepository.findByGtin(gtin);
            throw new BusinessRuleException(m.map(x -> "The code identifies REF " + x.getRef() + " but has no lot number. ")
                    .orElse("The code has no lot number. ") + "Scan the lot barcode or type the lot number.");
        }
        lotNumber = lotNumber.trim().toUpperCase();

        // 1. GTIN known: the material is certain, look up the lot (and expiry dates) inside it
        if (gtin != null) {
            Optional<Material> material = materialRepository.findByGtin(gtin);
            if (material.isPresent()) {
                List<Lot> candidates = lotRepository
                        .findByMaterialIdAndNumberIgnoreCaseOrderByExpiryDateAsc(material.get().getId(), lotNumber);
                return choose(candidates, expiryDate, hospital, lotNumber, material.get().getRef());
            }
        }

        // 2. GTIN unknown or absent: search by lot number, using the REF to narrow the materials
        List<Lot> candidates = lotRepository.findByNumber(lotNumber);
        if (ref != null) {
            String r = ref;
            candidates = candidates.stream().filter(l -> l.getMaterial().getRef().equalsIgnoreCase(r)).toList();
        }
        Result result = choose(candidates, expiryDate, hospital, lotNumber, ref);

        // Unambiguous match: learn the GTIN so the next scans of this REF resolve directly
        if (result.status() == Status.FOUND && gtin != null) {
            materialService.assignGtinIfMissing(result.lot().getMaterial(), gtin);
        }
        return result;
    }

    /**
     * Reads a scanned or typed code without requiring the lot to be registered (stock entries and deliveries).
     *  - GS1 (QR code or barcode): GTIN (01), lot (10) and expiry date (17);
     *  - bare GTIN (EAN barcode without lot): the material only;
     *  - labeled text ("REF: ... LOTE: ...");
     *  - plain text: a REF when it is registered; otherwise nothing is identified and the caller decides
     *    (e.g. the delivery screen treats it as a typed lot number).
     */
    @Transactional(readOnly = true)
    public ScannedCodeDTO read(String scannedCode) {
        String code = scannedCode == null ? "" : scannedCode.trim();
        if (code.isEmpty()) throw new BusinessRuleException("Code not provided");

        Map<String, String> gs1 = parseGs1(code);
        String gtin = MaterialService.normalizeGtin(gs1.get("01"));
        String lot = gs1.get("10");
        LocalDate expiryDate = parseExpiry(gs1.get("17"));
        String ref = null;

        if (gs1.isEmpty()) {
            if (code.matches("\\d{8}|\\d{12,14}")) {
                gtin = MaterialService.normalizeGtin(code);
            }
            Matcher ml = LOT_LABEL.matcher(code);
            if (ml.find()) lot = ml.group(1);
            Matcher mr = REF_LABEL.matcher(code);
            if (mr.find()) ref = mr.group(1).toUpperCase();
        }

        Optional<Material> material = gtin == null ? Optional.empty() : materialRepository.findByGtin(gtin);
        if (material.isEmpty() && ref != null) {
            material = materialRepository.findByRefIgnoreCase(ref);
        }
        if (material.isEmpty() && gs1.isEmpty() && lot == null && ref == null) {
            // Plain text (or digits that are not a known GTIN): a typed REF
            material = materialRepository.findByRefIgnoreCase(code.toUpperCase());
        }
        if (material.isPresent()) ref = material.get().getRef();

        return new ScannedCodeDTO(code, gtin, ref, lot == null || lot.isBlank() ? null : lot.trim().toUpperCase(),
                expiryDate, material.map(MaterialDTO::of).orElse(null));
    }

    /**
     * Picks one lot among the candidates (same number, possibly several materials or expiry dates):
     *  1. with the expiry date from the code, keeps only that date;
     *  2. if more than one remains, keeps the ones with balance inside the surgery hospital;
     *  3. exactly one left -> FOUND; none -> NOT_FOUND; several -> AMBIGUOUS (becomes a pending issue).
     */
    private Result choose(List<Lot> candidates, LocalDate expiryDate, Hospital hospital, String lotNumber, String ref) {
        List<Lot> list = candidates;
        if (expiryDate != null) {
            List<Lot> exact = list.stream().filter(l -> l.getExpiryDate().equals(expiryDate)).toList();
            // Lots typed as MM/AAAA are stored on day 1: the label's full date still matches by month
            YearMonth month = YearMonth.from(expiryDate);
            list = !exact.isEmpty() ? exact
                    : list.stream().filter(l -> YearMonth.from(l.getExpiryDate()).equals(month)).toList();
        }
        if (list.size() > 1 && hospital != null) {
            List<Lot> withBalance = list.stream()
                    .filter(l -> stockService.balance(l, hospital, Location.HOSPITAL) > 0)
                    .toList();
            if (!withBalance.isEmpty()) list = withBalance;
        }
        if (list.isEmpty()) return new Result(Status.NOT_FOUND, null, List.of(), lotNumber, ref);
        if (list.size() > 1) return new Result(Status.AMBIGUOUS, null, list, lotNumber, ref);
        return Result.found(list.get(0));
    }

    /** GS1 AI (17) is YYMMDD; day 00 means the last day of the month. Returns null when absent or invalid. */
    static LocalDate parseExpiry(String yymmdd) {
        if (yymmdd == null || !yymmdd.matches("\\d{6}")) return null;
        try {
            int year = 2000 + Integer.parseInt(yymmdd.substring(0, 2));
            int month = Integer.parseInt(yymmdd.substring(2, 4));
            int day = Integer.parseInt(yymmdd.substring(4, 6));
            YearMonth ym = YearMonth.of(year, month);
            return day == 0 ? ym.atEndOfMonth() : ym.atDay(day);
        } catch (java.time.DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    /** Extracts the GS1 Application Identifiers most common on implants: 01, 17, 11, 10, 21. */
    static Map<String, String> parseGs1(String code) {
        Map<String, String> ais = new LinkedHashMap<>();
        if (code.contains("(")) {
            Matcher m = GS1_PARENTHESES.matcher(code);
            while (m.find()) ais.put(m.group(1), m.group(2).trim());
            return ais;
        }
        String s = code.startsWith("]C1") || code.startsWith("]d2") || code.startsWith("]Q3")
                ? code.substring(3) : code;
        if (!s.startsWith("01") || s.length() < 16) return ais;
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == GS) { i++; continue; }
            if (i + 2 > s.length()) break;
            String ai = s.substring(i, i + 2);
            int fixed = switch (ai) {
                case "01" -> 14;
                case "11", "17" -> 6;
                default -> -1;
            };
            i += 2;
            if (fixed > 0) {
                if (i + fixed > s.length()) break;
                ais.put(ai, s.substring(i, i + fixed));
                i += fixed;
            } else if (ai.equals("10") || ai.equals("21")) {
                int end = s.indexOf(GS, i);
                if (end < 0) end = Math.min(s.length(), i + 20);
                ais.put(ai, s.substring(i, end));
                i = end;
            } else {
                break; // Unsupported AI: stop to avoid misparsing
            }
        }
        return ais;
    }
}

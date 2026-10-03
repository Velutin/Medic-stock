package com.project.mss.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import com.project.mss.dto.imports.ImportResultDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.MinimumStock;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.PriceImportMode;
import com.project.mss.repository.MinimumStockRepository;
import com.project.mss.repository.MaterialRepository;

/**
 * Excel spreadsheet import (.xls and .xlsx). Also the migration path for the
 * current data into PostgreSQL.
 *
 * Columns are located by header (case and accent insensitive). Header names follow the
 * Portuguese spreadsheets in use. The price table is the exception: REF in column A, value in column B.
 */
@Service
public class ImportService {

    public enum StockImportMode {
        /** Adds quantities to the current balance (incoming material). */
        ADD,
        /** Sets the balance to the spreadsheet value (inventory / initial load). */
        REPLACE
    }

    private static final DataFormatter FORMATTER = new DataFormatter(java.util.Locale.of("pt", "BR"));
    private static final int MAX_ERRORS = 200;

    private final MaterialService materialService;
    private final MaterialRepository materialRepository;
    private final StockService stockService;
    private final MinimumStockRepository minimumStockRepository;
    private final AccessControlService accessControlService;

    public ImportService(MaterialService materialService, MaterialRepository materialRepository,
                             StockService stockService, MinimumStockRepository minimumStockRepository,
                             AccessControlService accessControlService) {
        this.materialService = materialService;
        this.materialRepository = materialRepository;
        this.stockService = stockService;
        this.minimumStockRepository = minimumStockRepository;
        this.accessControlService = accessControlService;
    }

    // ============================================================ catalog

    /**
     * Columns: REF, DESCRIÇÃO, LINHA, [GTIN], [COMPONENTE], [TAMANHO], [COR].
     * LINHA accepts one or more lines separated by comma (e.g. "QUADRIL, JOELHO, OMBRO") and replaces
     * the current lines; it may be empty only for materials that already have lines.
     */
    @Transactional
    public ImportResultDTO materials(MultipartFile file) {
        accessControlService.requireManager();
        return process(file, new String[]{"REF"}, (row, col, errors) -> {
            String ref = text(row, col.get("REF"));
            if (ref.isBlank()) return false;
            String desc = text(row, col.getOrDefault("DESCRICAO", col.get("MATERIAL")));
            Material m = materialRepository.findByRefIgnoreCase(ref.trim()).orElseGet(Material::new);
            m.setRef(ref.trim().toUpperCase());
            if (!desc.isBlank()) m.setDescription(desc.trim());
            if (m.getDescription() == null) m.setDescription(m.getRef());
            String gtinText = text(row, col.get("GTIN"));
            if (!gtinText.isBlank()) {
                String gtin = MaterialService.normalizeGtin(gtinText);
                if (gtin == null) throw new IllegalArgumentException("invalid GTIN: " + gtinText);
                materialRepository.findByGtin(gtin)
                        .filter(other -> !other.getRef().equalsIgnoreCase(m.getRef()))
                        .ifPresent(other -> { throw new IllegalArgumentException("GTIN " + gtin + " already belongs to REF " + other.getRef()); });
                m.setGtin(gtin);
            }
            String linesText = text(row, col.get("LINHA"));
            if (!linesText.isBlank()) {
                var lines = com.project.mss.model.enums.ProductLine.fromLabels(linesText);
                m.getProductLines().clear();
                m.getProductLines().addAll(lines);
            }
            if (m.getProductLines().isEmpty()) {
                throw new IllegalArgumentException("LINHA is required (QUADRIL, JOELHO and/or OMBRO)");
            }
            String comp = text(row, col.get("COMPONENTE"));
            if (!comp.isBlank()) m.setComponent(comp.trim());
            String size = text(row, col.get("TAMANHO"));
            if (!size.isBlank()) m.setSize(size.trim());
            String color = text(row, col.get("COR"));
            if (color.matches("^#?[0-9A-Fa-f]{6}$")) m.setColor((color.startsWith("#") ? color : "#" + color).toUpperCase());
            materialRepository.save(m);
            return true;
        });
    }

    // ============================================================ prices

    /**
     * Hospital table: REF in column A and value in column B. Rows without a numeric value are skipped.
     * UPDATE creates or changes only the REFs in the spreadsheet. REPLACE treats the spreadsheet as the
     * complete table: values of REFs not in it are removed. REPLACE is all-or-nothing: if any row has an
     * error, nothing is saved, so a partial spreadsheet never wipes valid values.
     */
    @Transactional
    public ImportResultDTO prices(Long hospitalId, MultipartFile file, PriceImportMode mode, boolean createNewRefs) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        List<String> errors = new ArrayList<>();
        Set<Long> imported = new HashSet<>();
        int read = 0, ok = 0, ignored = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sh = wb.getSheetAt(0);
            for (Row row : sh) {
                String ref = text(row, 0).trim();
                BigDecimal value = number(row, 1);
                if (ref.isBlank() || value == null) { ignored++; continue; }
                read++;
                var material = materialRepository.findByRefIgnoreCase(ref);
                if (material.isEmpty() && !createNewRefs) {
                    annotate(errors, row, "REF " + ref + " is not in the catalog");
                    ignored++;
                    continue;
                }
                Material m = material.orElseGet(() -> materialService.getOrCreate(ref, null));
                materialService.setPrice(hospital, m, value.setScale(2, RoundingMode.HALF_UP));
                imported.add(m.getId());
                ok++;
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the spreadsheet: " + e.getMessage());
        }
        if (mode == PriceImportMode.REPLACE) {
            if (!errors.isEmpty()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                errors.add(0, "Complete table not applied: fix the rows below and import again");
                return new ImportResultDTO(read, 0, read, errors);
            }
            if (imported.isEmpty()) {
                throw new BusinessRuleException("The spreadsheet has no valid rows; the current table was kept");
            }
            materialService.removePricesExcept(hospital, imported);
        }
        return new ImportResultDTO(read, ok, ignored, errors);
    }

    // ============================================================ stock

    /**
     * Columns: REF, LOTE, VALIDADE, QUANTIDADE, [DESCRIÇÃO], [LOCAL], [GTIN].
     * LOCAL accepts SALA/STOREROOM or HOSPITAL; without the column, the given default location is used.
     */
    @Transactional
    public ImportResultDTO stock(Long hospitalId, MultipartFile file, Location defaultLocation,
                                          StockImportMode mode) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        return process(file, new String[]{"REF", "LOTE", "VALIDADE", "QUANTIDADE"}, (row, col, errors) -> {
            String ref = text(row, col.get("REF")).trim();
            String lotNumber = text(row, col.get("LOTE")).trim();
            BigDecimal qty = number(row, col.get("QUANTIDADE"));
            if (ref.isBlank() && lotNumber.isBlank()) return false;
            if (ref.isBlank() || lotNumber.isBlank()) throw new IllegalArgumentException("REF and lot are required");
            if (qty == null || qty.signum() < 0 || qty.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("invalid quantity");
            }

            Location local = defaultLocation;
            String localTxt = text(row, col.get("LOCAL"));
            if (!localTxt.isBlank()) {
                String n = normalize(localTxt);
                local = n.startsWith("SALA") || n.startsWith("STOREROOM") ? Location.STOREROOM : Location.HOSPITAL;
            }
            if (local == null && hospital.isDistributionCenter()) local = Location.STOREROOM;
            if (local == null) throw new IllegalArgumentException("location not provided (SALA/STOREROOM or HOSPITAL)");
            if (hospital.isDistributionCenter() && local == Location.HOSPITAL) {
                throw new IllegalArgumentException("a distribution center only keeps material in the storeroom (SALA)");
            }

            Material m = materialService.getOrCreate(ref, text(row, col.getOrDefault("DESCRICAO", col.get("MATERIAL"))));
            materialService.assignGtinIfMissing(m, text(row, col.get("GTIN")));
            LocalDate expiryDate = date(row, col.get("VALIDADE"));
            if (expiryDate == null) throw new IllegalArgumentException("expiry date is required");
            Lot lot = materialService.getOrCreateLot(m, lotNumber, expiryDate);

            if (mode == StockImportMode.REPLACE) {
                stockService.adjustBalance(lot, hospital, local, qty.intValue(), "Spreadsheet load: " + file.getOriginalFilename());
            } else if (qty.intValue() > 0) {
                stockService.recordEntry(lot, qty.intValue(), hospital, local,
                        "Spreadsheet import: " + file.getOriginalFilename());
            }
            return true;
        });
    }

    // ============================================================ minimums

    /** Colunas: REF, IDEAL, IDEAL TOTAL. */
    @Transactional
    public ImportResultDTO minimums(Long hospitalId, MultipartFile file) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        return process(file, new String[]{"REF", "IDEAL", "IDEALTOTAL"}, (row, col, errors) -> {
            String ref = text(row, col.get("REF")).trim();
            if (ref.isBlank()) return false;
            BigDecimal ideal = number(row, col.get("IDEAL"));
            BigDecimal idealTotal = number(row, col.get("IDEALTOTAL"));
            if (ideal == null || idealTotal == null) throw new IllegalArgumentException("IDEAL and IDEAL TOTAL are required");
            try {
                MinimumStockService.validate(hospital, ideal.intValue(), idealTotal.intValue());
            } catch (BusinessRuleException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            Material m = materialRepository.findByRefIgnoreCase(ref)
                    .orElseThrow(() -> new IllegalArgumentException("REF " + ref + " is not registered"));
            MinimumStock min = minimumStockRepository.findByHospitalIdAndMaterialId(hospital.getId(), m.getId())
                    .orElseGet(() -> {
                        MinimumStock created = new MinimumStock();
                        created.setHospital(hospital);
                        created.setMaterial(m);
                        return created;
                    });
            min.setHospitalIdeal(ideal.intValue());
            min.setIdealTotal(idealTotal.intValue());
            minimumStockRepository.save(min);
            return true;
        });
    }

    // ============================================================ generic engine

    @FunctionalInterface
    private interface RowProcessor {
        /** @return true if imported; false if the row was empty. Throws on row errors. */
        boolean process(Row row, Map<String, Integer> columns, List<String> errors);
    }

    private ImportResultDTO process(MultipartFile file, String[] required, RowProcessor proc) {
        List<String> errors = new ArrayList<>();
        int read = 0, ok = 0, ignored = 0;
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sh = wb.getSheetAt(0);
            int headerRow = -1;
            Map<String, Integer> columns = null;
            for (Row row : sh) {
                if (columns == null) {
                    Map<String, Integer> c = mapHeader(row);
                    boolean found = true;
                    for (String o : required) if (!c.containsKey(o)) { found = false; break; }
                    if (found) { columns = c; headerRow = row.getRowNum(); }
                    continue;
                }
                if (row.getRowNum() <= headerRow) continue;
                try {
                    if (proc.process(row, columns, errors)) { read++; ok++; }
                } catch (RuntimeException e) {
                    read++;
                    ignored++;
                    annotate(errors, row, e.getMessage());
                }
            }
            if (columns == null) {
                throw new BusinessRuleException("Header not found. Required columns: "
                        + String.join(", ", required));
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the spreadsheet: " + e.getMessage());
        }
        return new ImportResultDTO(read, ok, ignored, errors);
    }

    /** Normalizes column names and accepts common synonyms. */
    private Map<String, Integer> mapHeader(Row row) {
        Map<String, Integer> m = new HashMap<>();
        for (Cell cell : row) {
            String n = normalize(FORMATTER.formatCellValue(cell)).replaceAll("[^A-Z]", "");
            if (n.isEmpty()) continue;
            String key = switch (n) {
                case "REF", "REFERENCIA", "CODIGO", "COD", "CODREF" -> "REF";
                case "LOTE", "LOT", "NLOTE", "NUMEROLOTE" -> "LOTE";
                case "VALIDADE", "VAL", "VENCIMENTO", "DATAVALIDADE" -> "VALIDADE";
                case "QUANTIDADE", "QTD", "QTDE", "QUANT", "SALDO" -> "QUANTIDADE";
                case "DESCRICAO", "DESC" -> "DESCRICAO";
                case "MATERIAL", "PRODUTO", "ITEM" -> "MATERIAL";
                case "LOCAL", "LOCALIZACAO", "ONDE" -> "LOCAL";
                case "GTIN", "EAN", "CODIGODEBARRAS", "CODIGOBARRAS" -> "GTIN";
                case "LINHA", "LINHAS", "LINE", "LINES" -> "LINHA";
                case "IDEAL", "IDEALHOSPITAL", "MINIMO", "MINIMOHOSPITAL" -> "IDEAL";
                case "IDEALTOTAL", "TOTALIDEAL", "MINIMOTOTAL" -> "IDEALTOTAL";
                default -> n;
            };
            m.putIfAbsent(key, cell.getColumnIndex());
        }
        return m;
    }

    // ============================================================ cell reading

    private static String text(Row row, Integer col) {
        if (row == null || col == null) return "";
        Cell c = row.getCell(col);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c)) {
            double d = c.getNumericCellValue();
            if (d == Math.rint(d)) return String.valueOf((long) d); // numeric REF/lot without ".0"
        }
        return FORMATTER.formatCellValue(c).trim();
    }

    private static BigDecimal number(Row row, Integer col) {
        if (row == null || col == null) return null;
        Cell c = row.getCell(col);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(c.getNumericCellValue());
            if (c.getCellType() == CellType.FORMULA && c.getCachedFormulaResultType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(c.getNumericCellValue());
            }
            String s = FORMATTER.formatCellValue(c).replace("R$", "").replace(" ", "").trim();
            if (s.isEmpty()) return null;
            if (s.contains(",")) s = s.replace(".", "").replace(",", "."); // 1.234,56 -> 1234.56
            return new BigDecimal(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Accepts Excel dates, dd/MM/yyyy, dd/MM/yy, yyyy-MM-dd and MM/yyyy (last day of month). */
    private static LocalDate date(Row row, Integer col) {
        if (row == null || col == null) return null;
        Cell c = row.getCell(col);
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = FORMATTER.formatCellValue(c).trim();
        if (s.isEmpty()) return null;
        for (String p : new String[]{"dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "yyyy-MM-dd"}) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(p));
            } catch (RuntimeException ignored) { /* try next format */ }
        }
        for (String p : new String[]{"MM/yyyy", "M/yyyy", "MM/yy"}) {
            try {
                return YearMonth.parse(s, DateTimeFormatter.ofPattern(p)).atEndOfMonth();
            } catch (RuntimeException ignored) { /* try next format */ }
        }
        throw new IllegalArgumentException("invalid expiry date: " + s);
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase();
    }

    private static void annotate(List<String> errors, Row row, String msg) {
        if (errors.size() < MAX_ERRORS) errors.add("Row " + (row.getRowNum() + 1) + ": " + msg);
    }
}

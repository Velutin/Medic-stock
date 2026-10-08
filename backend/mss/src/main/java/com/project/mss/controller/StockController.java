package com.project.mss.controller;

import org.springdoc.core.annotations.ParameterObject;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.stock.StockAdjustmentDTO;
import com.project.mss.dto.stock.StockEntryDTO;
import com.project.mss.dto.stock.LotStockDTO;
import com.project.mss.dto.stock.StockSummaryDTO;
import com.project.mss.dto.stock.MaterialStockDTO;
import com.project.mss.dto.stock.StockRowDTO;
import com.project.mss.dto.stock.StockMovementDTO;
import com.project.mss.model.enums.Location;
import com.project.mss.service.StockService;
import com.project.mss.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/stock")
@Tag(name = "Stock", description = "Balances per hospital (inside the hospital and in the storeroom), entries and adjustments")
public class StockController {

    private final StockService stockService;
    private final ReportService reportService;

    public StockController(StockService stockService, ReportService reportService) {
        this.stockService = stockService;
        this.reportService = reportService;
    }

    @GetMapping("/hospital/{hospitalId}")
    @Operation(summary = "Hospital stock in spreadsheet layout (one row per lot)")
    public List<StockRowDTO> view(@PathVariable Long hospitalId,
                                       @RequestParam(defaultValue = "false") boolean includeExpired) {
        return stockService.hospitalView(hospitalId, includeExpired);
    }

    @GetMapping("/summary")
    @Operation(summary = "Stock summary of a hospital, without lots (surgical tech's view)",
               description = "Per material: quantity inside the hospital (storeroom excluded), valid lots only, "
                       + "with product lines for grouping.")
    public List<StockSummaryDTO> summary(@RequestParam Long hospitalId) {
        return stockService.hospitalSummary(hospitalId);
    }

    @GetMapping("/lots")
    @Operation(summary = "Stock by lot and hospital (administrators and read-only users; not surgical techs)",
               description = "Without hospitalId, searches every hospital the user can see (locates a lot anywhere). term matches lot "
                       + "number, REF, name, description or GTIN. Sorted by REF, expiry date and hospital. Paginated.")
    public Page<LotStockDTO> lots(@RequestParam(required = false) String term,
                                  @RequestParam(required = false) Long hospitalId,
                                  @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return stockService.searchLots(term, hospitalId, pageable);
    }

    @GetMapping("/storeroom")
    @Operation(summary = "Stock inside the storerooms, lot by lot (ADMIN)",
               description = "What is waiting to be transferred into the hospitals, including the storeroom of a "
                       + "distribution center. Without hospitalId, searches every storeroom the user can see. term "
                       + "matches lot number, REF, name, description or GTIN. Only lots with balance in the "
                       + "storeroom are listed, so hospitalQuantity comes back as zero. Paginated.")
    public Page<LotStockDTO> storeroom(@RequestParam(required = false) String term,
                                       @RequestParam(required = false) Long hospitalId,
                                       @RequestParam(defaultValue = "false") boolean misplaced,
                                       @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return stockService.searchStoreroomLots(term, hospitalId, misplaced, pageable);
    }

    @GetMapping("/material")
    @Operation(summary = "Stock of the materials matching a REF, name or description, per hospital and lot",
               description = "Partial match: '1032' returns every REF containing 1032; empty returns the whole catalog. "
                       + "Without hospitalId, returns every hospital visible to the user (useful for audits). "
                       + "Totals ignore expired lots. Paginated by material.")
    public Page<MaterialStockDTO> materialStock(@RequestParam(required = false) String term,
                                                @RequestParam(required = false) Long hospitalId,
                                                @RequestParam(defaultValue = "false") boolean includeExpired,
                                                @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return stockService.materialStock(term, hospitalId, includeExpired, pageable);
    }

    @GetMapping("/lots/xlsx")
    @Operation(summary = "Download the stock by lot as Excel, with the same filters of the screen",
               description = "Takes the same term and hospitalId of GET /stock/lots, so the file matches what the "
                       + "screen is showing, including expired lots. Without hospitalId, every hospital the user "
                       + "can see. Rows carry the size identification color.")
    public ResponseEntity<byte[]> lotsSpreadsheet(@RequestParam(required = false) String term,
                                                  @RequestParam(required = false) Long hospitalId) {
        return FileResponses.xlsx(reportService.lotSpreadsheet(term, hospitalId, Location.HOSPITAL, false),
                "estoque-por-lote.xlsx");
    }

    @GetMapping("/storeroom/xlsx")
    @Operation(summary = "Download the storeroom stock as Excel, with the same filters of the screen (ADMIN)",
               description = "Takes the same term and hospitalId of GET /stock/storeroom. Without hospitalId, every "
                       + "storeroom the user can see, each row saying which one it is. misplaced=true exports "
                       + "instead the balance sitting in the storeroom of a hospital supplied by a center.")
    public ResponseEntity<byte[]> storeroomSpreadsheet(@RequestParam(required = false) String term,
                                                       @RequestParam(required = false) Long hospitalId,
                                                       @RequestParam(defaultValue = "false") boolean misplaced) {
        return FileResponses.xlsx(reportService.lotSpreadsheet(term, hospitalId, Location.STOREROOM, misplaced),
                misplaced ? "estoque-na-sala-fora-do-lugar.xlsx" : "estoque-na-sala.xlsx");
    }

    @GetMapping("/hospital/{hospitalId}/xlsx")
    @Operation(summary = "Download the hospital stock as Excel, with size colors")
    public ResponseEntity<byte[]> spreadsheet(@PathVariable Long hospitalId) {
        return FileResponses.xlsx(reportService.stockSpreadsheet(hospitalId), "hospital-stock-" + hospitalId + ".xlsx");
    }

    @GetMapping("/hospital/{hospitalId}/movements")
    @Operation(summary = "Hospital stock movement history")
    public Page<StockMovementDTO> history(@PathVariable Long hospitalId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                           @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return stockService.history(hospitalId, start, end, pageable);
    }

    /*
     * Both answer 204 with no body. A String body is served as text/plain, and the screens read every answer
     * as JSON, so a plain sentence broke the screen after the operation had already been recorded.
     */

    @PostMapping("/entry")
    @Operation(summary = "Manual stock entry (ADMIN)")
    public ResponseEntity<Void> entry(@RequestBody @Valid StockEntryDTO dto) {
        stockService.manualEntry(dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/adjustment")
    @Operation(summary = "Inventory adjustment: sets the counted balance of a lot (ADMIN)")
    public ResponseEntity<Void> adjustment(@RequestBody @Valid StockAdjustmentDTO dto) {
        stockService.manualAdjustment(dto);
        return ResponseEntity.noContent().build();
    }
}

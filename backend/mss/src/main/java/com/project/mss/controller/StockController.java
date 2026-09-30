package com.project.mss.controller;

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
import com.project.mss.dto.stock.StockRowDTO;
import com.project.mss.dto.stock.StockMovementDTO;
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
                                           @PageableDefault(size = 50) Pageable pageable) {
        return stockService.history(hospitalId, start, end, pageable);
    }

    @PostMapping("/entry")
    @Operation(summary = "Manual stock entry (ADMIN)")
    public ResponseEntity<String> entry(@RequestBody @Valid StockEntryDTO dto) {
        stockService.manualEntry(dto);
        return ResponseEntity.ok("Entry recorded");
    }

    @PostMapping("/adjustment")
    @Operation(summary = "Inventory adjustment: sets the counted balance of a lot (ADMIN)")
    public ResponseEntity<String> adjustment(@RequestBody @Valid StockAdjustmentDTO dto) {
        stockService.manualAdjustment(dto);
        return ResponseEntity.ok("Balance adjusted");
    }
}

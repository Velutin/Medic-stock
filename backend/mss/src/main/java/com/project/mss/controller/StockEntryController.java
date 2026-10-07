package com.project.mss.controller;

import java.time.LocalDate;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.project.mss.dto.entry.EntryDTO;
import com.project.mss.dto.entry.EntryFormDTO;
import com.project.mss.dto.entry.EntryPreviewRowDTO;
import com.project.mss.dto.entry.LotRefConflictDTO;
import com.project.mss.service.ImportService;
import com.project.mss.service.LotRefChangeService;
import com.project.mss.service.StockEntryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/stock-entries")
@Tag(name = "Stock entries", description = "Material received from the supplier into the storeroom, grouped per receipt (ADMIN)")
public class StockEntryController {

    private final StockEntryService stockEntryService;
    private final ImportService importService;
    private final LotRefChangeService lotRefChangeService;

    public StockEntryController(StockEntryService stockEntryService, ImportService importService,
                                LotRefChangeService lotRefChangeService) {
        this.stockEntryService = stockEntryService;
        this.importService = importService;
        this.lotRefChangeService = lotRefChangeService;
    }

    @PostMapping
    @Operation(summary = "Register an entry: the lots enter the storeroom assigned to the destination",
               description = "The destination is a regular hospital or a distribution center (e.g. SESAB). Hospitals supplied "
                       + "by a distribution center are refused. Expired lots are refused; repeated lots are summed.")
    public ResponseEntity<EntryDTO> create(@RequestBody @Valid EntryFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockEntryService.create(dto));
    }

    @GetMapping
    @Operation(summary = "Entries in a period, newest first (default: everything until today)")
    public Page<EntryDTO> list(@RequestParam(required = false) Long hospitalId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                               @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return stockEntryService.list(hospitalId, start, end, pageable);
    }

    @GetMapping("/{id}")
    public EntryDTO find(@PathVariable Long id) {
        return stockEntryService.find(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Correct an entry (destination, date, REF, lot, expiry date, quantity)",
               description = "Replaces the entry content and applies only the differences to the storeroom. Refused when "
                       + "the material to be removed already left the storeroom.")
    public EntryDTO update(@PathVariable Long id, @RequestBody @Valid EntryFormDTO dto) {
        return stockEntryService.update(id, dto);
    }

    @PostMapping("/lot-conflicts")
    @Operation(summary = "Lots with the same number registered with another REF, for each material + lot to receive",
               description = "A lot number belongs to only one REF. Returns only the checks with conflicts; to receive "
                       + "them, the entry item is sent with changeRef = true and those lots become its REF. Lots already "
                       + "used in a surgery (usedInSurgery) cannot be changed.")
    public List<LotRefConflictDTO> lotConflicts(@RequestBody @Valid List<LotRefConflictDTO.Check> checks) {
        return lotRefChangeService.check(checks);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Read an entry spreadsheet for review, without saving",
               description = "Columns: REF, LOTE, VALIDADE, QUANTIDADE, [DESCRIÇÃO], [GTIN]. Unknown REFs come back "
                       + "without materialId; invalid or expired rows come back with the error.")
    public List<EntryPreviewRowDTO> preview(@RequestPart("file") MultipartFile file) {
        return importService.stockEntryPreview(file);
    }
}

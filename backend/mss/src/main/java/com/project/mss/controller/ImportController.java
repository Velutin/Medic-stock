package com.project.mss.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.project.mss.dto.imports.ImportResultDTO;
import com.project.mss.model.enums.Location;
import com.project.mss.service.ImportService;
import com.project.mss.service.ImportService.StockImportMode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/imports")
@Tag(name = "Imports", description = "Excel spreadsheet loading (.xls/.xlsx), also used for data migration")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Catalog columns: REF, DESCRIÇÃO, [GTIN], [COMPONENTE], [TAMANHO], [COR]")
    public ImportResultDTO materials(@RequestPart("file") MultipartFile file) {
        return importService.materials(file);
    }

    @PostMapping(value = "/hospital/{hospitalId}/prices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Hospital price table: REF in column A, value in column B")
    public ImportResultDTO prices(@PathVariable Long hospitalId,
                                         @RequestPart("file") MultipartFile file,
                                         @RequestParam(defaultValue = "false") boolean createNewRefs) {
        return importService.prices(hospitalId, file, createNewRefs);
    }

    @PostMapping(value = "/hospital/{hospitalId}/stock", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Stock columns: REF, LOTE, VALIDADE, QUANTIDADE, [DESCRIÇÃO], [LOCAL], [GTIN]",
               description = "mode=REPLACE for initial load/inventory; ADD for incoming material.")
    public ImportResultDTO stock(@PathVariable Long hospitalId,
                                          @RequestPart("file") MultipartFile file,
                                          @RequestParam(required = false) Location defaultLocation,
                                          @RequestParam(defaultValue = "ADD") StockImportMode mode) {
        return importService.stock(hospitalId, file, defaultLocation, mode);
    }

    @PostMapping(value = "/hospital/{hospitalId}/minimums", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Hospital minimums columns: REF, IDEAL, IDEAL TOTAL")
    public ImportResultDTO minimums(@PathVariable Long hospitalId,
                                          @RequestPart("file") MultipartFile file) {
        return importService.minimums(hospitalId, file);
    }
}

package com.project.mss.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.replenishment.MinimumPreviewRowDTO;
import com.project.mss.dto.replenishment.MinimumStockBatchDTO;
import com.project.mss.dto.replenishment.MinimumStockDTO;
import com.project.mss.dto.replenishment.MinimumStockFormDTO;
import com.project.mss.dto.replenishment.MinimumStockPatchDTO;
import com.project.mss.service.ImportService;
import com.project.mss.service.MinimumStockService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/hospitals/{hospitalId}/minimums")
@Tag(name = "Minimum stock", description = "Hospital minimum levels per REF used by the replenishment suggestion")
public class MinimumStockController {

    private final MinimumStockService minimumStockService;
    private final ImportService importService;

    public MinimumStockController(MinimumStockService minimumStockService, ImportService importService) {
        this.minimumStockService = minimumStockService;
        this.importService = importService;
    }

    @GetMapping
    @Operation(summary = "Minimum levels of the hospital, ordered by REF")
    public List<MinimumStockDTO> list(@PathVariable Long hospitalId) {
        return minimumStockService.list(hospitalId);
    }

    @PatchMapping
    @Operation(summary = "Change several REFs at once (ADMIN)",
               description = "Listed REFs are created or replaced; both levels 0 removes the REF from the list; "
                       + "REFs not listed are kept. One invalid row rejects the whole change.")
    public List<MinimumStockDTO> batch(@PathVariable Long hospitalId, @RequestBody @Valid MinimumStockBatchDTO dto) {
        return minimumStockService.batch(hospitalId, dto);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Read a minimums spreadsheet for review, without saving (ADMIN)",
               description = "Columns: REF, IDEAL, IDEAL TOTAL. Each row shows the current and the new levels, or the error.")
    public List<MinimumPreviewRowDTO> preview(@PathVariable Long hospitalId, @RequestPart("file") MultipartFile file) {
        return importService.minimumsPreview(hospitalId, file);
    }

    @PutMapping("/{materialId}")
    @Operation(summary = "Create or replace the minimum levels of a REF (ADMIN)")
    public MinimumStockDTO put(@PathVariable Long hospitalId, @PathVariable Long materialId,
                               @RequestBody @Valid MinimumStockFormDTO dto) {
        return minimumStockService.put(hospitalId, materialId, dto);
    }

    @PatchMapping("/{materialId}")
    @Operation(summary = "Change only the informed levels of a REF already in the list (ADMIN)")
    public MinimumStockDTO patch(@PathVariable Long hospitalId, @PathVariable Long materialId,
                                 @RequestBody @Valid MinimumStockPatchDTO dto) {
        return minimumStockService.patch(hospitalId, materialId, dto);
    }

    @DeleteMapping("/{materialId}")
    @Operation(summary = "Remove a REF from the hospital list (ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long hospitalId, @PathVariable Long materialId) {
        minimumStockService.delete(hospitalId, materialId);
        return ResponseEntity.noContent().build();
    }
}

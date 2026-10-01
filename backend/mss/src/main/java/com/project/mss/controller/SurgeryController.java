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

import com.project.mss.dto.surgery.SurgeryDTO;
import com.project.mss.dto.surgery.SurgeryFormDTO;
import com.project.mss.dto.surgery.SurgerySummaryDTO;
import com.project.mss.dto.surgery.SheetItemsDTO;
import com.project.mss.dto.surgery.WithdrawalResultDTO;
import com.project.mss.dto.surgery.WithdrawalItemDTO;
import com.project.mss.service.SurgeryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/surgeries")
@Tag(name = "Surgeries", description = "Material withdrawal in surgeries and consumption sheet")
public class SurgeryController {

    private final SurgeryService surgeryService;

    public SurgeryController(SurgeryService surgeryService) {
        this.surgeryService = surgeryService;
    }

    @PostMapping
    @Operation(summary = "Open a surgery (patient and date typed manually)")
    public ResponseEntity<SurgeryDTO> create(@RequestBody @Valid SurgeryFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(surgeryService.create(dto));
    }

    @GetMapping
    @Operation(summary = "List surgeries in a period (default: last 30 days)")
    public Page<SurgerySummaryDTO> list(@RequestParam(required = false) Long hospitalId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                          @ParameterObject @PageableDefault(size = 30) Pageable pageable) {
        return surgeryService.list(hospitalId, start, end, pageable);
    }

    @GetMapping("/{id}")
    public SurgeryDTO find(@PathVariable Long id) {
        return surgeryService.find(id);
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Record an item by QR code, GS1 barcode (GTIN + lot) or typed lot number",
               description = "If the lot does not exist, is expired, has no balance at the hospital or is ambiguous, a pending issue is created.")
    public WithdrawalResultDTO recordWithdrawal(@PathVariable Long id, @RequestBody @Valid WithdrawalItemDTO dto) {
        return surgeryService.recordWithdrawal(id, dto);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Remove an item recorded by mistake (returns it to the hospital stock)")
    public SurgeryDTO removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return surgeryService.removeItem(id, itemId);
    }

    @PostMapping(value = "/{id}/sheet", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach the consumption sheet (a PDF or photos; photos are converted to PDF)")
    public SurgeryDTO attachSheet(@PathVariable Long id, @RequestPart("files") List<MultipartFile> files) {
        return surgeryService.attachSheet(id, files);
    }

    @GetMapping("/{id}/sheet")
    @Operation(summary = "Download the attached consumption sheet")
    public ResponseEntity<byte[]> downloadSheet(@PathVariable Long id) {
        return FileResponses.pdf(surgeryService.downloadSheet(id), "surgery-sheet-" + id + ".pdf", true);
    }

    @PostMapping("/{id}/sheet/items")
    @Operation(summary = "Record the sheet labels (each label = 1 item)")
    public List<WithdrawalResultDTO> recordSheet(@PathVariable Long id, @RequestBody @Valid SheetItemsDTO dto) {
        return surgeryService.recordSheet(id, dto);
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Complete the surgery (requires no open pending issues)")
    public SurgeryDTO complete(@PathVariable Long id) {
        return surgeryService.complete(id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel the surgery and return its items to the hospital stock")
    public SurgeryDTO cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return surgeryService.cancel(id, reason);
    }
}

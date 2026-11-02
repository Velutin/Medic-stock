package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.replenishment.StoreroomReturnDTO;
import com.project.mss.dto.replenishment.StoreroomReturnFormDTO;
import com.project.mss.service.ReportService;
import com.project.mss.service.StoreroomReturnService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/storeroom-returns")
@Tag(name = "Storeroom returns", description = "Material taken out of a hospital and back into the storeroom (ADMIN)")
public class StoreroomReturnController {

    private final StoreroomReturnService storeroomReturnService;
    private final ReportService reportService;

    public StoreroomReturnController(StoreroomReturnService storeroomReturnService, ReportService reportService) {
        this.storeroomReturnService = storeroomReturnService;
        this.reportService = reportService;
    }

    @PostMapping
    @Operation(summary = "Take lots out of a hospital and back into the storeroom, creating a withdrawal",
               description = "The storeroom is resolved by the system: the hospital's own or, for a hospital "
                       + "supplied by a distribution center, the center's. Expired lots are allowed.")
    public ResponseEntity<StoreroomReturnDTO> create(@RequestBody @Valid StoreroomReturnFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeroomReturnService.create(dto));
    }

    @GetMapping
    @Operation(summary = "Latest withdrawals from a hospital")
    public List<StoreroomReturnDTO> list(@RequestParam Long hospitalId) {
        return storeroomReturnService.listByHospital(hospitalId);
    }

    @GetMapping("/{id}")
    public StoreroomReturnDTO find(@PathVariable Long id) {
        return storeroomReturnService.find(id);
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Withdrawal report (Classic layout, signed by the hospital handing the material over)")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return FileResponses.pdf(reportService.storeroomReturnPdf(id), "retirada-" + id + ".pdf", true);
    }
}

package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.replenishment.DeliveryDTO;
import com.project.mss.dto.replenishment.DeliveryFormDTO;
import com.project.mss.dto.replenishment.ShipmentStatusUpdateDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.service.DeliveryService;
import com.project.mss.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/deliveries")
@Tag(name = "Deliveries", description = "Storeroom to hospital deliveries, with signed delivery reports (ADMIN)")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final ReportService reportService;

    public DeliveryController(DeliveryService deliveryService, ReportService reportService) {
        this.deliveryService = deliveryService;
        this.reportService = reportService;
    }

    @PostMapping
    @Operation(summary = "Move lots from the storeroom into the hospital, creating a delivery",
               description = "sourceHospitalId is optional: by default the lots come from the storeroom balance of the "
                       + "distribution center that supplies the hospital (e.g. SESAB) or, for regular hospitals, "
                       + "of the hospital itself.")
    public ResponseEntity<DeliveryDTO> create(@RequestBody @Valid DeliveryFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.create(dto));
    }

    @GetMapping
    @Operation(summary = "Latest deliveries",
               description = "Filter by hospitalId (deliveries to a hospital) or sourceHospitalId "
                       + "(deliveries made from a distribution center storeroom). Exactly one is required.")
    public List<DeliveryDTO> list(@RequestParam(required = false) Long hospitalId,
                                  @RequestParam(required = false) Long sourceHospitalId) {
        if ((hospitalId == null) == (sourceHospitalId == null)) {
            throw new BusinessRuleException("Provide either hospitalId or sourceHospitalId");
        }
        return hospitalId != null
                ? deliveryService.listByHospital(hospitalId)
                : deliveryService.listBySource(sourceHospitalId);
    }

    @GetMapping("/dispatched")
    @Operation(summary = "Transfers still on the road, newest first (ADMIN)")
    public List<DeliveryDTO> dispatched() {
        return deliveryService.listDispatched();
    }

    @GetMapping("/{id}")
    public DeliveryDTO find(@PathVariable Long id) {
        return deliveryService.find(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Confirm the arrival of a transfer, or call it off",
               description = "ARRIVED moves what was received into the hospital; a line left out of items "
                       + "counts as fully received, and anything short stays on the road as a pending issue. "
                       + "CANCELLED puts everything back in the storeroom, and only while nothing has been "
                       + "confirmed.")
    public DeliveryDTO updateStatus(@PathVariable Long id, @RequestBody @Valid ShipmentStatusUpdateDTO dto) {
        return deliveryService.updateStatus(id, dto);
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Hospital delivery report (Classic layout, with signatures)")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return FileResponses.pdf(reportService.deliveryPdf(id), "delivery-" + id + ".pdf", true);
    }
}

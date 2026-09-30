package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.replenishment.DeliveryDTO;
import com.project.mss.dto.replenishment.ExecuteReplenishmentDTO;
import com.project.mss.dto.replenishment.SupplierOrderDTO;
import com.project.mss.dto.replenishment.SupplierOrderFormDTO;
import com.project.mss.dto.replenishment.ReplenishmentSuggestionDTO;
import com.project.mss.service.ReportService;
import com.project.mss.service.ReplenishmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/replenishment")
@Tag(name = "Replenishment", description = "Minimums, storeroom-to-hospital replenishment, deliveries and supplier orders")
public class ReplenishmentController {

    private final ReplenishmentService replenishmentService;
    private final ReportService reportService;

    public ReplenishmentController(ReplenishmentService replenishmentService, ReportService reportService) {
        this.replenishmentService = replenishmentService;
        this.reportService = reportService;
    }

    @GetMapping("/hospital/{hospitalId}/suggestion")
    @Operation(summary = "What to replenish from the storeroom and what to order from the supplier (expired lots are ignored)")
    public List<ReplenishmentSuggestionDTO> suggestion(@PathVariable Long hospitalId,
                                               @RequestParam(defaultValue = "true") boolean onlyWithShortage) {
        return replenishmentService.suggestion(hospitalId, onlyWithShortage);
    }

    @PostMapping("/deliveries")
    @Operation(summary = "Move lots from the storeroom into the hospital, creating a delivery (ADMIN)")
    public ResponseEntity<DeliveryDTO> execute(@RequestBody @Valid ExecuteReplenishmentDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(replenishmentService.executeReplenishment(dto));
    }

    @GetMapping("/hospital/{hospitalId}/deliveries")
    public List<DeliveryDTO> deliveries(@PathVariable Long hospitalId) {
        return replenishmentService.listDeliveries(hospitalId);
    }

    @GetMapping("/deliveries/{id}/pdf")
    @Operation(summary = "Hospital delivery report (Classic layout, with signatures)")
    public ResponseEntity<byte[]> deliveryPdf(@PathVariable Long id) {
        return FileResponses.pdf(reportService.deliveryPdf(id), "delivery-" + id + ".pdf", true);
    }

    @PostMapping("/orders")
    @Operation(summary = "Generate a reviewed supplier order (ADMIN)")
    public ResponseEntity<SupplierOrderDTO> generateOrder(@RequestBody @Valid SupplierOrderFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(replenishmentService.generateOrder(dto));
    }

    @GetMapping("/hospital/{hospitalId}/orders")
    public List<SupplierOrderDTO> orders(@PathVariable Long hospitalId) {
        return replenishmentService.listOrders(hospitalId);
    }

    @GetMapping("/orders/{id}/pdf")
    @Operation(summary = "Order PDF to send via WhatsApp")
    public ResponseEntity<byte[]> orderPdf(@PathVariable Long id) {
        return FileResponses.pdf(reportService.orderPdf(id), "order-" + id + ".pdf", false);
    }

    @PatchMapping("/orders/{id}/sent")
    @Operation(summary = "Mark the order as sent to the supplier (ADMIN)")
    public SupplierOrderDTO sent(@PathVariable Long id) {
        return replenishmentService.markSent(id);
    }
}

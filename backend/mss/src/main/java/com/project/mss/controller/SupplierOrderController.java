package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.replenishment.SupplierOrderDTO;
import com.project.mss.dto.replenishment.SupplierOrderFormDTO;
import com.project.mss.dto.replenishment.SupplierOrderStatusUpdateDTO;
import com.project.mss.service.ReportService;
import com.project.mss.service.SupplierOrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/supplier-orders")
@Tag(name = "Supplier orders", description = "Material orders to the supplier, sent as PDF via WhatsApp (ADMIN)")
public class SupplierOrderController {

    private final SupplierOrderService supplierOrderService;
    private final ReportService reportService;

    public SupplierOrderController(SupplierOrderService supplierOrderService, ReportService reportService) {
        this.supplierOrderService = supplierOrderService;
        this.reportService = reportService;
    }

    @PostMapping
    @Operation(summary = "Generate a reviewed supplier order")
    public ResponseEntity<SupplierOrderDTO> create(@RequestBody @Valid SupplierOrderFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierOrderService.create(dto));
    }

    @GetMapping
    @Operation(summary = "Latest supplier orders of a hospital")
    public List<SupplierOrderDTO> list(@RequestParam Long hospitalId) {
        return supplierOrderService.listByHospital(hospitalId);
    }

    @GetMapping("/{id}")
    public SupplierOrderDTO find(@PathVariable Long id) {
        return supplierOrderService.find(id);
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Order PDF to send via WhatsApp")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return FileResponses.pdf(reportService.orderPdf(id), "order-" + id + ".pdf", false);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change the supplier order status",
               description = "Use SENT once the order PDF has been sent to the supplier.")
    public SupplierOrderDTO updateStatus(@PathVariable Long id, @RequestBody @Valid SupplierOrderStatusUpdateDTO dto) {
        return supplierOrderService.updateStatus(id, dto);
    }
}

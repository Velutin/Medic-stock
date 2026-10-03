package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.billing.BillingDTO;
import com.project.mss.dto.billing.BillingRateDTO;
import com.project.mss.dto.billing.BillingRateFormDTO;
import com.project.mss.service.BillingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Billing", description = "Completed surgeries billing and billing rates (ADMIN)")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    // ------------------------------------------------------------ billing

    @GetMapping("/billing")
    @Operation(summary = "Billing of completed surgeries",
               description = "months: one or more months in yyyy-MM (e.g. months=2026-09&months=2026-10); "
                       + "default: current month. Surgeries count in the month they were completed. "
                       + "Without hospitalId: every hospital.")
    public BillingDTO billing(@RequestParam(required = false) List<String> months,
                              @RequestParam(required = false) Long hospitalId) {
        return billingService.billing(months, hospitalId);
    }

    @GetMapping("/billing/xlsx")
    @Operation(summary = "Billing as spreadsheet (same filters)")
    public ResponseEntity<byte[]> billingSpreadsheet(@RequestParam(required = false) List<String> months,
                                                     @RequestParam(required = false) Long hospitalId) {
        return FileResponses.xlsx(billingService.billingSpreadsheet(months, hospitalId), "billing.xlsx");
    }

    @GetMapping("/billing/pdf")
    @Operation(summary = "Billing as PDF (same filters)")
    public ResponseEntity<byte[]> billingPdf(@RequestParam(required = false) List<String> months,
                                             @RequestParam(required = false) Long hospitalId) {
        return FileResponses.pdf(billingService.billingPdf(months, hospitalId), "billing.pdf", true);
    }

    // ------------------------------------------------------------ rates

    @GetMapping("/billing-rates")
    @Operation(summary = "Billing rates, newest first",
               description = "Each rate applies from its start month until the next one.")
    public List<BillingRateDTO> rates() {
        return billingService.listRates();
    }

    @PutMapping("/billing-rates/{month}")
    @Operation(summary = "Create or change the rates starting in a month (yyyy-MM)",
               description = "Only the current or a future month: past billing never changes.")
    public BillingRateDTO putRate(@PathVariable String month, @RequestBody @Valid BillingRateFormDTO dto) {
        return billingService.putRate(month, dto);
    }

    @DeleteMapping("/billing-rates/{month}")
    @Operation(summary = "Remove the rates starting in a month (current or future only)",
               description = "The previous rates go on applying.")
    public ResponseEntity<Void> deleteRate(@PathVariable String month) {
        billingService.deleteRate(month);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

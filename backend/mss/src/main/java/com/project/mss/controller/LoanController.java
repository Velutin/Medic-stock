package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.loan.LoanDTO;
import com.project.mss.dto.loan.LoanFormDTO;
import com.project.mss.model.enums.LoanStatus;
import com.project.mss.service.LoanService;
import com.project.mss.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/loans")
@Tag(name = "Loans", description = "Material loans between hospitals (source: hospital or storeroom)")
public class LoanController {

    private final LoanService loanService;
    private final ReportService reportService;

    public LoanController(LoanService loanService, ReportService reportService) {
        this.loanService = loanService;
        this.reportService = reportService;
    }

    @PostMapping
    @Operation(summary = "Record a loan (debits the source and credits the destination hospital)")
    public ResponseEntity<LoanDTO> create(@RequestBody @Valid LoanFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.create(dto));
    }

    @GetMapping
    @Operation(summary = "List loans (use status=PENDING_NOTIFICATION to see what still has to be reported to the supplier)")
    public List<LoanDTO> list(@RequestParam(required = false) LoanStatus status) {
        return loanService.list(status);
    }

    @PatchMapping("/{id}/notified")
    @Operation(summary = "Mark the supplier as notified (ADMIN)")
    public LoanDTO notified(@PathVariable Long id) {
        return loanService.markNotified(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return FileResponses.pdf(reportService.loanPdf(id), "emprestimo-" + id + ".pdf", true);
    }
}

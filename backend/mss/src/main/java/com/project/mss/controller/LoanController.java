package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.loan.LoanDTO;
import com.project.mss.dto.loan.LoanFormDTO;
import com.project.mss.model.enums.LoanType;
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
    @Operation(summary = "Record a loan or a return to the supplier",
               description = "LOAN (default): debits the source and credits the destination hospital. RETURN: debits the "
                       + "source only (the material goes back to the supplier); returnReason is required, expired lots are accepted "
                       + "and the return has its own PDF.")
    public ResponseEntity<LoanDTO> create(@RequestBody @Valid LoanFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.create(dto));
    }

    @GetMapping
    @Operation(summary = "Latest 50 loans and returns (type=LOAN or RETURN to filter)")
    public List<LoanDTO> list(@RequestParam(required = false) LoanType type) {
        return loanService.list(type);
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Loan PDF (printed as a delivery to the destination hospital) or, for type RETURN, the return PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return FileResponses.pdf(reportService.loanPdf(id), "entrega-E-" + id + ".pdf", true);
    }
}

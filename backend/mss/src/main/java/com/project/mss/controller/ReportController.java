package com.project.mss.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.report.WeeklySurgeriesDTO;
import com.project.mss.service.ReportService;
import org.springframework.http.ResponseEntity;
import com.project.mss.dto.loan.LoanDTO;
import com.project.mss.dto.report.CancellationReportDTO;
import com.project.mss.dto.report.DeliveryReportDTO;
import com.project.mss.dto.report.PendingIssueReportDTO;
import com.project.mss.dto.report.ValidityReportDTO;
import com.project.mss.dto.report.WeeklyClosingDTO;
import com.project.mss.dto.stock.StockMovementDTO;
import com.project.mss.dto.surgery.SurgeryDTO;
import com.project.mss.model.enums.LoanType;
import com.project.mss.model.enums.MovementType;
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.service.ReportQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/reports")
@Tag(name = "Reports", description = "Weekly surgery closing")
public class ReportController {

    private final ReportService reportService;

    private final ReportQueryService reportQueryService;

    public ReportController(ReportService reportService, ReportQueryService reportQueryService) {
        this.reportService = reportService;
        this.reportQueryService = reportQueryService;
    }

    @GetMapping("/weekly-surgeries")
    @Operation(summary = "Surgeries per week (Saturday to Friday) by hospital and surgical tech (ADMIN)")
    public List<WeeklySurgeriesDTO> weeklySurgeries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return reportService.surgeriesByWeek(start, end);
    }

    // ============================================================ reports screen (preview + PDF with the same filters)

    @GetMapping("/weekly-closing")
    @Operation(summary = "Weekly closing (Saturday to Friday) containing the date: open and completed surgeries per hospital (ADMIN)")
    public WeeklyClosingDTO weeklyClosing(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(required = false) Long hospitalId) {
        return reportQueryService.weeklyClosing(date, hospitalId);
    }

    @GetMapping("/weekly-closing/pdf")
    public ResponseEntity<byte[]> weeklyClosingPdf(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(required = false) Long hospitalId) {
        return FileResponses.pdf(reportQueryService.weeklyClosingPdf(date, hospitalId), "cirurgias-da-semana.pdf", false);
    }

    @GetMapping("/cancellations")
    @Operation(summary = "Cancelled surgeries by who recorded them, with the share over everything they recorded (ADMIN)")
    public CancellationReportDTO cancellations(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId) {
        return reportQueryService.cancellations(start, end, hospitalId);
    }

    @GetMapping("/cancellations/pdf")
    public ResponseEntity<byte[]> cancellationsPdf(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId) {
        return FileResponses.pdf(reportQueryService.cancellationsPdf(start, end, hospitalId), "cancelamentos.pdf", false);
    }

    @GetMapping("/pending-issues")
    @Operation(summary = "Pending issues of surgery withdrawals in the period (ADMIN)")
    public List<PendingIssueReportDTO> pendingIssues(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId,
                                                     @RequestParam(required = false) PendingIssueStatus status) {
        return reportQueryService.pendingIssues(start, end, hospitalId, status);
    }

    @GetMapping("/pending-issues/pdf")
    public ResponseEntity<byte[]> pendingIssuesPdf(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId,
                                                   @RequestParam(required = false) PendingIssueStatus status) {
        return FileResponses.pdf(reportQueryService.pendingIssuesPdf(start, end, hospitalId, status), "pendencias.pdf", false);
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Delivery documents already generated in the period: transfers and loans delivered to hospitals (ADMIN)")
    public List<DeliveryReportDTO> deliveries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId) {
        return reportQueryService.deliveries(start, end, hospitalId);
    }

    @GetMapping("/consumption")
    @Operation(summary = "Completed surgeries in the period with items, lots and table values (ADMIN)")
    public List<SurgeryDTO> consumption(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId,
                                        @RequestParam(required = false) String patient) {
        return reportQueryService.consumption(start, end, hospitalId, patient);
    }

    @GetMapping("/consumption/pdf")
    public ResponseEntity<byte[]> consumptionPdf(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId,
                                                 @RequestParam(required = false) String patient) {
        return FileResponses.pdf(reportQueryService.consumptionPdf(start, end, hospitalId, patient), "consumo.pdf", false);
    }

    @GetMapping("/validity")
    @Operation(summary = "Lots expired or expiring within the window (days), inside the hospitals and in the storerooms (ADMIN)")
    public List<ValidityReportDTO> validity(@RequestParam(required = false) Long hospitalId,
                                            @RequestParam(defaultValue = "30") int days) {
        return reportQueryService.validity(hospitalId, days);
    }

    @GetMapping("/validity/pdf")
    public ResponseEntity<byte[]> validityPdf(@RequestParam(required = false) Long hospitalId,
                                              @RequestParam(defaultValue = "30") int days) {
        return FileResponses.pdf(reportQueryService.validityPdf(hospitalId, days), "validade.pdf", false);
    }

    @GetMapping("/loans")
    @Operation(summary = "Loans (type=LOAN) or returns to the supplier (type=RETURN) in the period (ADMIN)")
    public List<LoanDTO> loans(@RequestParam(defaultValue = "LOAN") LoanType type, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                               @RequestParam(required = false) Long hospitalId) {
        return reportQueryService.loans(type, start, end, hospitalId);
    }

    @GetMapping("/loans/pdf")
    public ResponseEntity<byte[]> loansPdf(@RequestParam(defaultValue = "LOAN") LoanType type, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                           @RequestParam(required = false) Long hospitalId) {
        return FileResponses.pdf(reportQueryService.loansPdf(type, start, end, hospitalId),
                type == LoanType.RETURN ? "devolucoes.pdf" : "emprestimos.pdf", false);
    }

    @GetMapping("/movements")
    @Operation(summary = "Stock movements in the period (at most 3000, newest first) (ADMIN)")
    public List<StockMovementDTO> movements(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId,
                                            @RequestParam(required = false) MovementType type) {
        return reportQueryService.movements(start, end, hospitalId, type);
    }

    @GetMapping("/movements/pdf")
    public ResponseEntity<byte[]> movementsPdf(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(required = false) Long hospitalId,
                                               @RequestParam(required = false) MovementType type) {
        return FileResponses.pdf(reportQueryService.movementsPdf(start, end, hospitalId, type), "movimentacoes.pdf", false);
    }
}

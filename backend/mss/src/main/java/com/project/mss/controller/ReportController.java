package com.project.mss.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.report.WeeklySurgeriesDTO;
import com.project.mss.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/reports")
@Tag(name = "Reports", description = "Weekly surgery closing")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/weekly-surgeries")
    @Operation(summary = "Surgeries per week (Saturday to Friday) by hospital and surgical tech (ADMIN)")
    public List<WeeklySurgeriesDTO> weeklySurgeries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return reportService.surgeriesByWeek(start, end);
    }
}

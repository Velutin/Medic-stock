package com.project.mss.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.mss.dto.dashboard.DashboardDTO;
import com.project.mss.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Administrator's dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Dashboard indicators (ADMIN)",
               description = "Stock units (expired lots excluded), lots expiring in 30 days, expired lots, "
                       + "surgeries of the closing week (Saturday to Friday), replenishment alerts and latest movements. "
                       + "Without hospitalId: every hospital.")
    public DashboardDTO dashboard(@RequestParam(required = false) Long hospitalId) {
        return dashboardService.dashboard(hospitalId);
    }
}

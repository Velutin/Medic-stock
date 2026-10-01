package com.project.mss.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.surgery.PendingIssueDTO;
import com.project.mss.dto.surgery.PendingIssueStatusUpdateDTO;
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.service.PendingIssueService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/pending-issues")
@Tag(name = "Pending issues", description = "Scans that could not be recorded automatically")
public class PendingIssueController {

    private final PendingIssueService pendingIssueService;

    public PendingIssueController(PendingIssueService pendingIssueService) {
        this.pendingIssueService = pendingIssueService;
    }

    @GetMapping
    public List<PendingIssueDTO> list(@RequestParam(required = false) PendingIssueStatus status) {
        return pendingIssueService.list(status);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change the pending issue status (ADMIN)",
               description = "RESOLVED requires lotId and records that lot in the surgery. "
                       + "DISCARDED closes the issue without a lot. Both require a resolution text.")
    public PendingIssueDTO updateStatus(@PathVariable Long id, @RequestBody @Valid PendingIssueStatusUpdateDTO dto) {
        return pendingIssueService.updateStatus(id, dto);
    }
}

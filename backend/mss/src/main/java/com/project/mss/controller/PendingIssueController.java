package com.project.mss.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.surgery.PendingIssueDTO;
import com.project.mss.dto.surgery.ResolvePendingIssueDTO;
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

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve (providing the correct lot) or discard (without lotId) (ADMIN)")
    public PendingIssueDTO resolve(@PathVariable Long id, @RequestBody @Valid ResolvePendingIssueDTO dto) {
        return pendingIssueService.resolve(id, dto);
    }
}

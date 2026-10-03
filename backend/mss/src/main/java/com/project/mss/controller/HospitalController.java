package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.hospital.CoveredHospitalsDTO;
import com.project.mss.dto.hospital.HospitalDTO;
import com.project.mss.dto.hospital.HospitalFormDTO;
import com.project.mss.dto.hospital.PriceDTO;
import com.project.mss.dto.hospital.PriceFormDTO;
import com.project.mss.dto.replenishment.ReplenishmentSuggestionDTO;
import com.project.mss.service.HospitalService;
import com.project.mss.service.MaterialService;
import com.project.mss.service.ReplenishmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/hospitals")
@Tag(name = "Hospitals", description = "Served hospitals, price tables and surgical tech assignment")
public class HospitalController {

    private final HospitalService hospitalService;
    private final MaterialService materialService;
    private final ReplenishmentService replenishmentService;

    public HospitalController(HospitalService hospitalService, MaterialService materialService,
                              ReplenishmentService replenishmentService) {
        this.hospitalService = hospitalService;
        this.materialService = materialService;
        this.replenishmentService = replenishmentService;
    }

    @GetMapping
    @Operation(summary = "Hospitals visible to the logged-in user",
               description = "includeInactive=true (ADMIN) also lists inactive hospitals, for the registry screen.")
    public List<HospitalDTO> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return hospitalService.listVisible(includeInactive);
    }

    @GetMapping("/{id}")
    public HospitalDTO find(@PathVariable Long id) {
        return hospitalService.find(id);
    }

    @PostMapping
    @Operation(summary = "Create hospital (ADMIN)")
    public ResponseEntity<HospitalDTO> create(@RequestBody @Valid HospitalFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hospitalService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a hospital, including its price table type (SIGTAP or TENDER) (ADMIN)")
    public HospitalDTO update(@PathVariable Long id, @RequestBody @Valid HospitalFormDTO dto) {
        return hospitalService.update(id, dto);
    }

    @GetMapping("/{id}/prices")
    @Operation(summary = "Hospital price table (ADMIN)")
    public List<PriceDTO> prices(@PathVariable Long id) {
        return materialService.hospitalTable(id);
    }

    @PutMapping("/{id}/prices/{materialId}")
    @Operation(summary = "Create or change the value of a REF in the hospital table (ADMIN)",
               description = "Surgery items recorded without value for this REF receive it automatically; "
                       + "items that already had a value keep it.")
    public PriceDTO putPrice(@PathVariable Long id, @PathVariable Long materialId,
                             @RequestBody @Valid PriceFormDTO dto) {
        return materialService.putPrice(id, materialId, dto.value());
    }

    @GetMapping("/{id}/replenishment-suggestions")
    @Operation(summary = "What to replenish from the storeroom and what to order from the supplier (ADMIN)",
               description = "Expired lots are ignored. onlyWithShortage=false also lists materials at their ideal level.")
    public List<ReplenishmentSuggestionDTO> replenishmentSuggestions(@PathVariable Long id,
                                                                     @RequestParam(defaultValue = "true") boolean onlyWithShortage) {
        return replenishmentService.suggestion(id, onlyWithShortage);
    }

    @PutMapping("/{id}/covered-hospitals")
    @Operation(summary = "Set the hospitals supplied by a distribution center (ADMIN)",
               description = "A hospital can belong to only one distribution center. An empty list removes every hospital.")
    public HospitalDTO setCoveredHospitals(@PathVariable Long id, @RequestBody @Valid CoveredHospitalsDTO dto) {
        return hospitalService.setCoveredHospitals(id, dto);
    }

}

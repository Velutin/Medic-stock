package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.hospital.HospitalDTO;
import com.project.mss.dto.hospital.HospitalFormDTO;
import com.project.mss.dto.hospital.PriceDTO;
import com.project.mss.dto.hospital.UserHospitalsDTO;
import com.project.mss.service.HospitalService;
import com.project.mss.service.MaterialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/hospitals")
@Tag(name = "Hospitals", description = "Served hospitals, price tables and surgical tech assignment")
public class HospitalController {

    private final HospitalService hospitalService;
    private final MaterialService materialService;

    public HospitalController(HospitalService hospitalService, MaterialService materialService) {
        this.hospitalService = hospitalService;
        this.materialService = materialService;
    }

    @GetMapping
    @Operation(summary = "Hospitals visible to the logged-in user")
    public List<HospitalDTO> list() {
        return hospitalService.listVisible();
    }

    @GetMapping("/{id}")
    public HospitalDTO find(@PathVariable Long id) {
        return hospitalService.find(id);
    }

    @PostMapping
    @Operation(summary = "Cadastrar hospital (ADMIN)")
    public ResponseEntity<HospitalDTO> create(@RequestBody @Valid HospitalFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hospitalService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a hospital, including its price table type (SIGTAP or TENDER) (ADMIN)")
    public HospitalDTO update(@PathVariable Long id, @RequestBody @Valid HospitalFormDTO dto) {
        return hospitalService.update(id, dto);
    }

    @GetMapping("/{id}/prices")
    @Operation(summary = "Hospital price table")
    public List<PriceDTO> prices(@PathVariable Long id) {
        return materialService.hospitalTable(id);
    }

    @PutMapping("/users")
    @Operation(summary = "Set which hospitals a user works at (ADMIN)")
    public ResponseEntity<String> setUserHospitals(@RequestBody @Valid UserHospitalsDTO dto) {
        hospitalService.setUserHospitals(dto);
        return ResponseEntity.ok("User hospitals updated");
    }
}

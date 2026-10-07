package com.project.mss.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.stock.LotBalanceDTO;
import com.project.mss.dto.material.LotDTO;
import com.project.mss.dto.material.MaterialDTO;
import com.project.mss.dto.material.MaterialFormDTO;
import com.project.mss.dto.material.ScannedCodeDTO;
import com.project.mss.service.StockService;
import com.project.mss.service.MaterialService;
import com.project.mss.service.LotScanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/materials")
@Tag(name = "Materials", description = "Material catalog (REF) and lots")
public class MaterialController {

    private final MaterialService materialService;
    private final StockService stockService;
    private final LotScanService lotScanService;

    public MaterialController(MaterialService materialService, StockService stockService,
                              LotScanService lotScanService) {
        this.materialService = materialService;
        this.stockService = stockService;
        this.lotScanService = lotScanService;
    }

    @GetMapping
    @Operation(summary = "Search materials by REF or description")
    public Page<MaterialDTO> find(@RequestParam(required = false) String term,
                                    @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return materialService.find(term, pageable);
    }

    @PostMapping
    @Operation(summary = "Cadastrar material (ADMIN)")
    public ResponseEntity<MaterialDTO> create(@RequestBody @Valid MaterialFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materialService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar material (ADMIN)")
    public MaterialDTO update(@PathVariable Long id, @RequestBody @Valid MaterialFormDTO dto) {
        return materialService.update(id, dto);
    }

    @GetMapping("/scan")
    @Operation(summary = "What a scanned or typed code identifies (GTIN, REF, lot, expiry date), without requiring the lot to exist",
               description = "Accepts GS1 QR codes/barcodes, a bare GTIN, labeled text (REF/LOTE) or a typed REF.")
    public ScannedCodeDTO scan(@RequestParam String code) {
        return lotScanService.read(code);
    }

    @GetMapping("/lots")
    @Operation(summary = "Search lots by number")
    public List<LotDTO> findLots(@RequestParam String number) {
        return materialService.findLots(number);
    }

    @GetMapping("/lots/{lotId}/balances")
    @Operation(summary = "Where the lot is (hospital and storeroom) across visible hospitals")
    public List<LotBalanceDTO> lotBalances(@PathVariable Long lotId) {
        return stockService.findLotLocations(lotId);
    }
}

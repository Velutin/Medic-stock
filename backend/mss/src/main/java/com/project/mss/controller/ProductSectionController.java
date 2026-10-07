package com.project.mss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.dto.section.ProductSectionDTO;
import com.project.mss.dto.section.ProductSectionFormDTO;
import com.project.mss.service.ProductSectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/product-sections")
@Tag(name = "Product sections", description = "Catalog sections used to group the hospital stock by material (ADMIN)")
public class ProductSectionController {

    private final ProductSectionService productSectionService;

    public ProductSectionController(ProductSectionService productSectionService) {
        this.productSectionService = productSectionService;
    }

    @GetMapping
    @Operation(summary = "Sections in display order, with the number of items in each")
    public List<ProductSectionDTO> list() {
        return productSectionService.list();
    }

    @PostMapping
    public ResponseEntity<ProductSectionDTO> create(@RequestBody @Valid ProductSectionFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productSectionService.create(dto));
    }

    @PutMapping("/{id}")
    public ProductSectionDTO update(@PathVariable Long id, @RequestBody @Valid ProductSectionFormDTO dto) {
        return productSectionService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a section without items (sections with items can be deactivated)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productSectionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

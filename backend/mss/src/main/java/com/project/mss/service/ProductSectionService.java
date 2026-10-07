package com.project.mss.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.section.ProductSectionDTO;
import com.project.mss.dto.section.ProductSectionFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.ProductSection;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.ProductSectionRepository;

/** Catalog sections (e.g. "Quadril não cimentada"), used to group the hospital stock by material. */
@Service
public class ProductSectionService {

    private final ProductSectionRepository productSectionRepository;
    private final MaterialRepository materialRepository;
    private final AccessControlService accessControlService;

    public ProductSectionService(ProductSectionRepository productSectionRepository, MaterialRepository materialRepository,
                                 AccessControlService accessControlService) {
        this.productSectionRepository = productSectionRepository;
        this.materialRepository = materialRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<ProductSectionDTO> list() {
        accessControlService.requireManager();
        return productSectionRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(s -> ProductSectionDTO.of(s, materialRepository.countBySectionId(s.getId())))
                .toList();
    }

    @Transactional
    public ProductSectionDTO create(ProductSectionFormDTO dto) {
        accessControlService.requireManager();
        ProductSection s = new ProductSection();
        apply(s, dto);
        return ProductSectionDTO.of(productSectionRepository.save(s), 0);
    }

    @Transactional
    public ProductSectionDTO update(Long id, ProductSectionFormDTO dto) {
        accessControlService.requireManager();
        ProductSection s = load(id);
        apply(s, dto);
        return ProductSectionDTO.of(productSectionRepository.save(s), materialRepository.countBySectionId(id));
    }

    /** Only sections without items can be removed; otherwise deactivate them. */
    @Transactional
    public void delete(Long id) {
        accessControlService.requireManager();
        ProductSection s = load(id);
        long materials = materialRepository.countBySectionId(id);
        if (materials > 0) {
            throw new BusinessRuleException("Section " + s.getName() + " has " + materials
                    + " items: move them to another section or deactivate it");
        }
        productSectionRepository.delete(s);
    }

    private ProductSection load(Long id) {
        return productSectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section " + id + " not found"));
    }

    private void apply(ProductSection s, ProductSectionFormDTO dto) {
        String name = dto.name().trim().replaceAll("\\s+", " ");
        productSectionRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(s.getId()))
                .ifPresent(other -> { throw new BusinessRuleException("Section " + name + " already exists"); });
        s.setName(name);
        s.setDisplayOrder(dto.displayOrder());
        if (dto.active() != null) s.setActive(dto.active());
    }
}

package com.project.mss.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.replenishment.SupplierOrderDTO;
import com.project.mss.dto.replenishment.SupplierOrderFormDTO;
import com.project.mss.dto.replenishment.SupplierOrderStatusUpdateDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.SupplierOrder;
import com.project.mss.model.entity.SupplierOrderItem;
import com.project.mss.model.enums.OrderStatus;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.SupplierOrderRepository;

/** Material orders to the supplier, sent as PDF via WhatsApp. */
@Service
public class SupplierOrderService {

    private final SupplierOrderRepository supplierOrderRepository;
    private final MaterialRepository materialRepository;
    private final AccessControlService accessControlService;

    public SupplierOrderService(SupplierOrderRepository supplierOrderRepository, MaterialRepository materialRepository,
                                AccessControlService accessControlService) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.materialRepository = materialRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public SupplierOrderDTO create(SupplierOrderFormDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());

        SupplierOrder p = new SupplierOrder();
        p.setHospital(hospital);
        p.setNotes(dto.notes());
        p.setCreatedBy(accessControlService.currentUser());
        for (SupplierOrderFormDTO.SupplierOrderRequestItem i : dto.items()) {
            Material m = materialRepository.findById(i.materialId())
                    .orElseThrow(() -> new EntityNotFoundException("Material " + i.materialId() + " not found"));
            SupplierOrderItem item = new SupplierOrderItem();
            item.setSupplierOrder(p);
            item.setMaterial(m);
            item.setQuantity(i.quantity());
            item.setUrgent(i.urgent() == null || i.urgent());
            p.getItems().add(item);
        }
        return SupplierOrderDTO.of(supplierOrderRepository.save(p));
    }

    /** Applies a status transition requested through PATCH /supplier-orders/{id}. */
    @Transactional
    public SupplierOrderDTO updateStatus(Long id, SupplierOrderStatusUpdateDTO dto) {
        accessControlService.requireManager();
        SupplierOrder p = load(id);
        switch (dto.status()) {
            case SENT -> {
                if (p.getStatus() == OrderStatus.SENT) {
                    throw new BusinessRuleException("Order " + id + " was already sent");
                }
                p.setStatus(OrderStatus.SENT);
                p.setSentAt(LocalDateTime.now());
            }
            case GENERATED -> throw new BusinessRuleException("A sent order cannot return to generated");
        }
        return SupplierOrderDTO.of(supplierOrderRepository.save(p));
    }

    @Transactional(readOnly = true)
    public List<SupplierOrderDTO> listByHospital(Long hospitalId) {
        accessControlService.requireHospitalAccess(hospitalId);
        return supplierOrderRepository.findTop50ByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                .map(SupplierOrderDTO::of).toList();
    }

    @Transactional(readOnly = true)
    public SupplierOrderDTO find(Long id) {
        return SupplierOrderDTO.of(load(id));
    }

    /** Loads the order and ensures the current user can access its hospital. */
    @Transactional(readOnly = true)
    public SupplierOrder load(Long id) {
        SupplierOrder p = supplierOrderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order " + id + " not found"));
        accessControlService.requireHospitalAccess(p.getHospital().getId());
        return p;
    }
}

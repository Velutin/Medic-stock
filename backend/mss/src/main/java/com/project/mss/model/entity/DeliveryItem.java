package com.project.mss.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "delivery_item")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * How many units were confirmed on arrival. Null while the shipment is on the road; on arrival it is
     * what was actually in the box, which is quantity in the ordinary case and less when something is
     * missing. The difference is what the pending issue is about.
     */
    @Column(name = "arrived_quantity")
    private Integer arrivedQuantity;


    public DeliveryItem(Delivery delivery, Lot lot, Integer quantity) {
        this.delivery = delivery;
        this.lot = lot;
        this.quantity = quantity;
    }
}

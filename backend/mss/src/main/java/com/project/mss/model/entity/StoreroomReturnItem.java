package com.project.mss.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "storeroom_return_item")
@Getter
@Setter
@NoArgsConstructor
public class StoreroomReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id")
    private StoreroomReturn storeroomReturn;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(nullable = false)
    private Integer quantity;

    public StoreroomReturnItem(StoreroomReturn storeroomReturn, Lot lot, Integer quantity) {
        this.storeroomReturn = storeroomReturn;
        this.lot = lot;
        this.quantity = quantity;
    }
}

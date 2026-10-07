package com.project.mss.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One lot received in a stock entry (a lot appears only once per entry). */
@Entity
@Table(name = "stock_entry_item")
@Getter
@Setter
@NoArgsConstructor
public class StockEntryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_entry_id")
    private StockEntry stockEntry;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(nullable = false)
    private Integer quantity;

    public StockEntryItem(StockEntry stockEntry, Lot lot, Integer quantity) {
        this.stockEntry = stockEntry;
        this.lot = lot;
        this.quantity = quantity;
    }
}

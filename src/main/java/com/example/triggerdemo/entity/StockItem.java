package com.example.triggerdemo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "stock",
    uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "variant_id"})
)
public class StockItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public StockItem() {}

    public StockItem(Warehouse warehouse, ProductVariant variant, Integer quantity, LocalDateTime updatedAt) {
        this.warehouse = warehouse;
        this.variant = variant;
        this.quantity = quantity;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Warehouse getWarehouse() { return warehouse; }
    public ProductVariant getVariant() { return variant; }
    public Integer getQuantity() { return quantity; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setId(Long id) { this.id = id; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public void setVariant(ProductVariant variant) { this.variant = variant; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

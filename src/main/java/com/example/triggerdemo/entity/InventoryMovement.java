package com.example.triggerdemo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private InventoryMovementType movementType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected InventoryMovement() {}

    public InventoryMovement(
            Warehouse warehouse,
            ProductVariant variant,
            Order order,
            InventoryMovementType movementType,
            Integer quantity,
            Integer quantityBefore,
            Integer quantityAfter,
            String note,
            LocalDateTime createdAt) {
        this.warehouse = warehouse;
        this.variant = variant;
        this.order = order;
        this.movementType = movementType;
        this.quantity = quantity;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Warehouse getWarehouse() { return warehouse; }
    public ProductVariant getVariant() { return variant; }
    public Order getOrder() { return order; }
    public InventoryMovementType getMovementType() { return movementType; }
    public Integer getQuantity() { return quantity; }
    public Integer getQuantityBefore() { return quantityBefore; }
    public Integer getQuantityAfter() { return quantityAfter; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

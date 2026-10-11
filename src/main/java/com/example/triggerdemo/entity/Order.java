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
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "stock_deducted", nullable = false)
    private boolean stockDeducted;

    public Order() {}

    public Order(String orderNumber, String recipientName, String phone, String deliveryAddress,
                 OrderStatus status, LocalDateTime createdAt, Warehouse warehouse) {
        this.orderNumber = orderNumber;
        this.recipientName = recipientName;
        this.phone = phone;
        this.deliveryAddress = deliveryAddress;
        this.status = status;
        this.createdAt = createdAt;
        this.warehouse = warehouse;
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Warehouse getWarehouse() { return warehouse; }
    public boolean isStockDeducted() { return stockDeducted; }
    public void setId(Long id) { this.id = id; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public void setStockDeducted(boolean stockDeducted) { this.stockDeducted = stockDeducted; }
}

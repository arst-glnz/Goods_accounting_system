package com.example.triggerdemo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "old_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getOldPrice() { return oldPrice; }
    public BigDecimal getNewPrice() { return newPrice; }
    public LocalDateTime getChangedAt() { return changedAt; }
}

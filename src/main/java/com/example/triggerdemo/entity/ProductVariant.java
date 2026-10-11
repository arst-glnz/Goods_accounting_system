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

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 50)
    private String size;

    @Column(length = 100)
    private String modifier;

    @Column(length = 100)
    private String distress;

    @Column(length = 100)
    private String stains;

    public ProductVariant() {}

    public ProductVariant(Product product, String size, String modifier, String distress, String stains) {
        this.product = product;
        this.size = size;
        this.modifier = modifier;
        this.distress = distress;
        this.stains = stains;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSize() { return size; }
    public String getModifier() { return modifier; }
    public String getDistress() { return distress; }
    public String getStains() { return stains; }
    public void setId(Long id) { this.id = id; }
    public void setProduct(Product product) { this.product = product; }
    public void setSize(String size) { this.size = size; }
    public void setModifier(String modifier) { this.modifier = modifier; }
    public void setDistress(String distress) { this.distress = distress; }
    public void setStains(String stains) { this.stains = stains; }
}

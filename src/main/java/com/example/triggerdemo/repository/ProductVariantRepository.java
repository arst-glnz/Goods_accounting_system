package com.example.triggerdemo.repository;

import com.example.triggerdemo.entity.ProductVariant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProduct_IdOrderByIdAsc(Long productId);

    @Query("""
            SELECT v FROM ProductVariant v
            JOIN FETCH v.product
            ORDER BY v.product.name, v.size, v.id
            """)
    List<ProductVariant> findAllWithProduct();
}

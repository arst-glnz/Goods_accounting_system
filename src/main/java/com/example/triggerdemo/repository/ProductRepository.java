package com.example.triggerdemo.repository;

import com.example.triggerdemo.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query(
            value = """
                    SELECT p FROM Product p
                    WHERE (:query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')))
                      AND (:stockFilter = 'all'
                           OR (:stockFilter = 'available' AND p.stockQuantity > 5)
                           OR (:stockFilter = 'low' AND p.stockQuantity BETWEEN 1 AND 5)
                           OR (:stockFilter = 'out' AND p.stockQuantity = 0))
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Product p
                    WHERE (:query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')))
                      AND (:stockFilter = 'all'
                           OR (:stockFilter = 'available' AND p.stockQuantity > 5)
                           OR (:stockFilter = 'low' AND p.stockQuantity BETWEEN 1 AND 5)
                           OR (:stockFilter = 'out' AND p.stockQuantity = 0))
                    """)
    Page<Product> search(
            @Param("query") String query,
            @Param("stockFilter") String stockFilter,
            Pageable pageable);
}

package com.example.triggerdemo.repository;

import com.example.triggerdemo.entity.StockItem;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {
    @Query("""
            SELECT s FROM StockItem s
            JOIN FETCH s.warehouse
            JOIN FETCH s.variant v
            JOIN FETCH v.product
            ORDER BY v.product.name, v.size, s.id
            """)
    List<StockItem> findInventoryWithDetails();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM StockItem s
            WHERE s.warehouse.id = :warehouseId AND s.variant.id = :variantId
            """)
    Optional<StockItem> findLocked(
            @Param("warehouseId") Long warehouseId,
            @Param("variantId") Long variantId);

    @Query("""
            SELECT COALESCE(SUM(s.quantity), 0)
            FROM StockItem s
            WHERE s.variant.product.id = :productId
            """)
    long sumQuantityByProductId(@Param("productId") Long productId);

    List<StockItem> findAllByVariant_Id(Long variantId);
}

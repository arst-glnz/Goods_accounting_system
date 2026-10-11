package com.example.triggerdemo.repository;

import com.example.triggerdemo.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findAllByOrder_IdOrderByIdAsc(Long orderId);
    boolean existsByVariant_Id(Long variantId);
}

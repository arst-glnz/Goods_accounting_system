package com.example.triggerdemo.repository;

import com.example.triggerdemo.entity.InventoryMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findTop100ByOrderByCreatedAtDescIdDesc();
    boolean existsByVariant_Id(Long variantId);
}

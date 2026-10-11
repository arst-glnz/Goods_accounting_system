package com.example.triggerdemo.repository;

import com.example.triggerdemo.entity.ProductCreationLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCreationLogRepository extends JpaRepository<ProductCreationLog, Long> {
    List<ProductCreationLog> findAllByOrderByCreatedAtDesc();
}

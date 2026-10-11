package com.example.triggerdemo.repository;

import com.example.triggerdemo.entity.Warehouse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByName(String name);
}

package com.example.triggerdemo.service;

import com.example.triggerdemo.entity.InventoryMovement;
import com.example.triggerdemo.entity.InventoryMovementType;
import com.example.triggerdemo.entity.Order;
import com.example.triggerdemo.entity.OrderItem;
import com.example.triggerdemo.entity.Product;
import com.example.triggerdemo.entity.ProductVariant;
import com.example.triggerdemo.entity.StockItem;
import com.example.triggerdemo.entity.Warehouse;
import com.example.triggerdemo.repository.InventoryMovementRepository;
import com.example.triggerdemo.repository.ProductRepository;
import com.example.triggerdemo.repository.ProductVariantRepository;
import com.example.triggerdemo.repository.StockItemRepository;
import com.example.triggerdemo.repository.WarehouseRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockMovementService {
    private final StockItemRepository stockRepository;
    private final ProductVariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    public StockMovementService(
            StockItemRepository stockRepository,
            ProductVariantRepository variantRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            InventoryMovementRepository movementRepository) {
        this.stockRepository = stockRepository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public void receive(Long variantId, Long warehouseId, int quantity, String note) {
        applyManualMovement(variantId, warehouseId, quantity, note, InventoryMovementType.RECEIPT);
    }

    @Transactional
    public void issue(Long variantId, Long warehouseId, int quantity, String note) {
        applyManualMovement(variantId, warehouseId, quantity, note, InventoryMovementType.ISSUE);
    }

    @Transactional
    public void deductForOrder(Order order, List<OrderItem> items) {
        applyOrderMovement(order, items, true);
    }

    @Transactional
    public void returnForOrder(Order order, List<OrderItem> items) {
        applyOrderMovement(order, items, false);
    }

    private void applyManualMovement(
            Long variantId,
            Long warehouseId,
            int quantity,
            String note,
            InventoryMovementType movementType) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество должно быть больше нуля.");
        }

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Вариант не найден"));
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Склад не найден"));
        StockItem stock = stockRepository.findLocked(warehouseId, variantId)
                .orElseThrow(() -> new IllegalStateException("Для варианта не создана строка остатка."));

        int before = stock.getQuantity();
        int after;
        if (movementType == InventoryMovementType.ISSUE) {
            if (before < quantity) {
                throw new InsufficientStockException(
                        "Недостаточно остатка: доступно " + before + ", запрошено " + quantity + ".");
            }
            after = before - quantity;
        } else {
            after = Math.addExact(before, quantity);
        }

        stock.setQuantity(after);
        stock.setUpdatedAt(LocalDateTime.now());
        stockRepository.save(stock);
        movementRepository.save(new InventoryMovement(
                warehouse, variant, null, movementType, quantity, before, after,
                normalizeNote(note), LocalDateTime.now()));
        refreshProductTotal(variant.getProduct());
    }

    private void applyOrderMovement(Order order, List<OrderItem> items, boolean deduct) {
        Map<Long, Integer> quantitiesByVariant = new LinkedHashMap<>();
        for (OrderItem item : items) {
            quantitiesByVariant.merge(item.getVariant().getId(), item.getQuantity(), Math::addExact);
        }

        List<Long> variantIds = quantitiesByVariant.keySet().stream().sorted().toList();
        Map<Long, StockItem> lockedStocks = new LinkedHashMap<>();
        for (Long variantId : variantIds) {
            StockItem stock = stockRepository.findLocked(order.getWarehouse().getId(), variantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Для варианта " + variantId + " не создана строка остатка."));
            lockedStocks.put(variantId, stock);
        }

        if (deduct) {
            for (Long variantId : variantIds) {
                StockItem stock = lockedStocks.get(variantId);
                int requested = quantitiesByVariant.get(variantId);
                if (stock.getQuantity() < requested) {
                    String productName = stock.getVariant().getProduct().getName();
                    throw new InsufficientStockException(
                            "Недостаточно остатка для товара «" + productName + "»: доступно "
                                    + stock.getQuantity() + ", требуется " + requested + ".");
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        InventoryMovementType movementType = deduct
                ? InventoryMovementType.ORDER_ISSUE
                : InventoryMovementType.ORDER_RETURN;
        for (Long variantId : variantIds) {
            StockItem stock = lockedStocks.get(variantId);
            int quantity = quantitiesByVariant.get(variantId);
            int before = stock.getQuantity();
            int after = deduct ? before - quantity : Math.addExact(before, quantity);
            stock.setQuantity(after);
            stock.setUpdatedAt(now);
            stockRepository.save(stock);
            movementRepository.save(new InventoryMovement(
                    order.getWarehouse(), stock.getVariant(), order, movementType,
                    quantity, before, after,
                    deduct ? "Списание по заказу " + order.getOrderNumber()
                            : "Возврат по заказу " + order.getOrderNumber(),
                    now));
        }

        List<Product> changedProducts = new ArrayList<>();
        for (StockItem stock : lockedStocks.values()) {
            Product product = stock.getVariant().getProduct();
            if (!changedProducts.contains(product)) {
                changedProducts.add(product);
            }
        }
        changedProducts.forEach(this::refreshProductTotal);
    }

    private void refreshProductTotal(Product product) {
        product.setStockQuantity(Math.toIntExact(stockRepository.sumQuantityByProductId(product.getId())));
        productRepository.save(product);
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}

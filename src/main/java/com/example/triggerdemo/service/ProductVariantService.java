package com.example.triggerdemo.service;

import com.example.triggerdemo.entity.Product;
import com.example.triggerdemo.entity.ProductVariant;
import com.example.triggerdemo.entity.StockItem;
import com.example.triggerdemo.entity.Warehouse;
import com.example.triggerdemo.repository.ProductRepository;
import com.example.triggerdemo.repository.ProductVariantRepository;
import com.example.triggerdemo.repository.StockItemRepository;
import com.example.triggerdemo.repository.WarehouseRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductVariantService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockRepository;

    public ProductVariantService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            WarehouseRepository warehouseRepository,
            StockItemRepository stockRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public ProductVariant create(long productId, VariantForm form) {
        Product product = findProduct(productId);
        ensureUnique(product, form, null);
        ProductVariant variant = new ProductVariant(
                product, normalize(form.getSize()), normalize(form.getModifier()),
                normalize(form.getDistress()), normalize(form.getStains()));
        variant = variantRepository.save(variant);
        Warehouse warehouse = getDefaultWarehouse();
        stockRepository.save(new StockItem(warehouse, variant, 0, LocalDateTime.now()));
        return variant;
    }

    @Transactional
    public void update(long productId, long variantId, VariantForm form) {
        Product product = findProduct(productId);
        ProductVariant variant = variantRepository.findById(variantId)
                .filter(existing -> existing.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Вариант не найден"));
        ensureUnique(product, form, variantId);
        variant.setSize(normalize(form.getSize()));
        variant.setModifier(normalize(form.getModifier()));
        variant.setDistress(normalize(form.getDistress()));
        variant.setStains(normalize(form.getStains()));
        variantRepository.save(variant);
    }

    @Transactional(readOnly = true)
    public List<ProductVariant> findByProductId(long productId) {
        findProduct(productId);
        return variantRepository.findByProduct_IdOrderByIdAsc(productId);
    }

    private Product findProduct(long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));
    }

    private Warehouse getDefaultWarehouse() {
        return warehouseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Не настроен основной склад."));
    }

    private void ensureUnique(Product product, VariantForm form, Long excludedId) {
        boolean duplicate = variantRepository.findByProduct_IdOrderByIdAsc(product.getId()).stream()
                .filter(existing -> !existing.getId().equals(excludedId))
                .anyMatch(existing -> equal(existing.getSize(), normalize(form.getSize()))
                        && equal(existing.getModifier(), normalize(form.getModifier())));
        if (duplicate) {
            throw new DuplicateVariantException("Вариант с такими размером и модификатором уже существует.");
        }
    }

    private boolean equal(String first, String second) {
        return first == null ? second == null : first.equalsIgnoreCase(second);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static class VariantForm {
        @jakarta.validation.constraints.Size(max = 50, message = "Размер не должен превышать 50 символов")
        private String size;

        @jakarta.validation.constraints.Size(max = 100, message = "Модификатор не должен превышать 100 символов")
        private String modifier;

        @jakarta.validation.constraints.Size(max = 100, message = "Параметр distress не должен превышать 100 символов")
        private String distress;

        @jakarta.validation.constraints.Size(max = 100, message = "Параметр stains не должен превышать 100 символов")
        private String stains;

        public String getSize() { return size; }
        public String getModifier() { return modifier; }
        public String getDistress() { return distress; }
        public String getStains() { return stains; }
        public void setSize(String size) { this.size = size; }
        public void setModifier(String modifier) { this.modifier = modifier; }
        public void setDistress(String distress) { this.distress = distress; }
        public void setStains(String stains) { this.stains = stains; }
    }

    public static class DuplicateVariantException extends RuntimeException {
        public DuplicateVariantException(String message) {
            super(message);
        }
    }
}

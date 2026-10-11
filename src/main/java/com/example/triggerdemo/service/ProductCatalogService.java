package com.example.triggerdemo.service;

import com.example.triggerdemo.entity.Product;
import com.example.triggerdemo.entity.ProductVariant;
import com.example.triggerdemo.entity.StockItem;
import com.example.triggerdemo.repository.InventoryMovementRepository;
import com.example.triggerdemo.repository.OrderItemRepository;
import com.example.triggerdemo.repository.ProductRepository;
import com.example.triggerdemo.repository.ProductVariantRepository;
import com.example.triggerdemo.repository.StockItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCatalogService {
    private final ProductRepository productRepository;
    private final ProductVariantService variantService;
    private final ProductVariantRepository variantRepository;
    private final StockItemRepository stockRepository;
    private final InventoryMovementRepository movementRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductCatalogService(
            ProductRepository productRepository,
            ProductVariantService variantService,
            ProductVariantRepository variantRepository,
            StockItemRepository stockRepository,
            InventoryMovementRepository movementRepository,
            OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.variantService = variantService;
        this.variantRepository = variantRepository;
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public Product create(Product product) {
        product.setStockQuantity(0);
        Product saved = productRepository.save(product);
        ProductVariantService.VariantForm initialVariant = new ProductVariantService.VariantForm();
        initialVariant.setSize("ONE SIZE");
        initialVariant.setModifier("base");
        initialVariant.setDistress("none");
        initialVariant.setStains("none");
        variantService.create(saved.getId(), initialVariant);
        return saved;
    }

    @Transactional
    public void deleteIfUnused(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
        for (ProductVariant variant : variantRepository.findByProduct_IdOrderByIdAsc(productId)) {
            List<StockItem> stocks = stockRepository.findAllByVariant_Id(variant.getId());
            if (stocks.stream().anyMatch(stock -> stock.getQuantity() != 0)
                    || movementRepository.existsByVariant_Id(variant.getId())
                    || orderItemRepository.existsByVariant_Id(variant.getId())) {
                throw new ProductDeletionBlockedException(
                        "Товар нельзя удалить: по его вариантам уже есть складские операции или заказы.");
            }
            stockRepository.deleteAll(stocks);
            variantRepository.delete(variant);
        }
        productRepository.delete(product);
    }

    public static class ProductDeletionBlockedException extends RuntimeException {
        public ProductDeletionBlockedException(String message) {
            super(message);
        }
    }
}

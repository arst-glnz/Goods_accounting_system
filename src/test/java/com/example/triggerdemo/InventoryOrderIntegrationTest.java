package com.example.triggerdemo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.triggerdemo.entity.InventoryMovementType;
import com.example.triggerdemo.entity.Order;
import com.example.triggerdemo.entity.OrderStatus;
import com.example.triggerdemo.entity.StockItem;
import com.example.triggerdemo.repository.InventoryMovementRepository;
import com.example.triggerdemo.repository.OrderRepository;
import com.example.triggerdemo.repository.ProductRepository;
import com.example.triggerdemo.repository.StockItemRepository;
import com.example.triggerdemo.service.InsufficientStockException;
import com.example.triggerdemo.service.OrderService;
import com.example.triggerdemo.service.OrderService.OrderForm;
import com.example.triggerdemo.service.OrderService.OrderLineForm;
import com.example.triggerdemo.service.StockMovementService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class InventoryOrderIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("goods_accounting")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StockItemRepository stockRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private StockMovementService stockMovementService;
    @Autowired private OrderService orderService;

    @Test
    @Transactional
    void initializesSchemaAndKeepsProductTotalsInSyncWithVariantStock() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema()
                """, String.class);

        assertThat(tables).contains(
                "roles", "users", "products", "product_variants",
                "warehouses", "stock", "orders", "order_items",
                "inventory_movements", "audit_log", "product_creation_log");
        assertThat(stockRepository.findInventoryWithDetails()).isNotEmpty();

        Map<Long, Long> stockByProduct = new java.util.HashMap<>();
        for (StockItem stock : stockRepository.findInventoryWithDetails()) {
            Long productId = Objects.requireNonNull(stock.getVariant().getProduct().getId());
            long previousQuantity = stockByProduct.getOrDefault(productId, 0L);
            stockByProduct.put(productId, previousQuantity + stock.getQuantity());
        }
        stockByProduct.forEach((productId, quantity) ->
                assertThat(productRepository.findById(Objects.requireNonNull(productId))
                        .orElseThrow().getStockQuantity()).isEqualTo(quantity.intValue()));
    }

    @Test
    @Transactional
    void postgresTriggersWriteCreationAndPriceAuditRows() {
        String probeName = "test-trigger-" + java.util.UUID.randomUUID();
        Long productId = jdbcTemplate.queryForObject(
                "INSERT INTO products(name, price, stock_quantity) VALUES (?, 12.00, 0) RETURNING id",
                Long.class,
                probeName);

        jdbcTemplate.update("UPDATE products SET price = 13.00 WHERE id = ?", productId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM product_creation_log WHERE product_id = ? AND product_name = ?",
                Integer.class,
                productId,
                probeName)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM audit_log
                WHERE product_id = ? AND product_name = ? AND old_price = 12.00 AND new_price = 13.00
                """,
                Integer.class,
                productId,
                probeName)).isEqualTo(1);
    }

    @Test
    @Transactional
    void receiptAndIssueRecordMovementsAndUpdateAggregateQuantity() {
        StockItem stock = stockRepository.findInventoryWithDetails().getFirst();
        int before = stock.getQuantity();
        long receiptsBefore = movementRepository.count();

        stockMovementService.receive(stock.getVariant().getId(), stock.getWarehouse().getId(), 2, "test receipt");
        stockMovementService.issue(stock.getVariant().getId(), stock.getWarehouse().getId(), 1, "test issue");

        assertThat(stockRepository.findLocked(stock.getWarehouse().getId(), stock.getVariant().getId())
                .orElseThrow().getQuantity()).isEqualTo(before + 1);
        assertThat(productRepository.findById(
                Objects.requireNonNull(stock.getVariant().getProduct().getId()))
                .orElseThrow().getStockQuantity()).isEqualTo(before + 1);
        assertThat(movementRepository.count()).isEqualTo(receiptsBefore + 2);
        assertThat(movementRepository.findAll()).extracting("movementType")
                .contains(InventoryMovementType.RECEIPT, InventoryMovementType.ISSUE);
    }

    @Test
    @Transactional
    void orderConfirmationDeductsAtomicallyCancellationRestoresAndCompletionKeepsDeduction() {
        List<StockItem> available = stockRepository.findInventoryWithDetails().stream()
                .filter(stock -> stock.getQuantity() > 0)
                .collect(Collectors.toMap(
                        stock -> stock.getVariant().getId(),
                        stock -> stock,
                        (first, ignored) -> first))
                .values().stream()
                .limit(2)
                .toList();
        assertThat(available).hasSize(2);

        Map<Long, Integer> before = quantitiesByVariant(available);
        Order cancelledOrder = orderService.create(orderForm(available, 1));
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(quantitiesByVariant(available)).isEqualTo(before);

        orderService.confirm(cancelledOrder.getId());
        assertThat(orderRepository.findById(Objects.requireNonNull(cancelledOrder.getId()))
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PROCESSING);
        assertThat(quantitiesByVariant(available))
                .containsEntry(available.getFirst().getVariant().getId(),
                        before.get(available.getFirst().getVariant().getId()) - 1);

        orderService.cancel(cancelledOrder.getId());
        assertThat(orderRepository.findById(Objects.requireNonNull(cancelledOrder.getId()))
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(quantitiesByVariant(available)).isEqualTo(before);

        Order completedOrder = orderService.create(orderForm(available, 1));
        orderService.confirm(completedOrder.getId());
        orderService.complete(completedOrder.getId());
        assertThat(orderRepository.findById(Objects.requireNonNull(completedOrder.getId()))
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(quantitiesByVariant(available))
                .containsEntry(available.getFirst().getVariant().getId(),
                        before.get(available.getFirst().getVariant().getId()) - 1);
    }

    @Test
    @Transactional
    void calculateOrderTotalUsesPricesSavedOnOrderItems() {
        StockItem stock = stockRepository.findInventoryWithDetails().stream()
                .filter(item -> item.getQuantity() > 0)
                .findFirst()
                .orElseThrow();
        BigDecimal savedUnitPrice = stock.getVariant().getProduct().getPrice();
        Order order = orderService.create(orderForm(List.of(stock), 2));

        assertThat(orderService.calculateTotal(Objects.requireNonNull(order.getId())))
                .isEqualByComparingTo(savedUnitPrice.multiply(BigDecimal.valueOf(2)));

        jdbcTemplate.update(
                "UPDATE products SET price = price + 100 WHERE id = ?",
                stock.getVariant().getProduct().getId());

        assertThat(orderService.calculateTotal(Objects.requireNonNull(order.getId())))
                .isEqualByComparingTo(savedUnitPrice.multiply(BigDecimal.valueOf(2)));
    }

    @Test
    @Transactional
    void insufficientStockLeavesOrderAndAllStockUnchanged() {
        StockItem stock = stockRepository.findInventoryWithDetails().stream()
                .filter(item -> item.getQuantity() >= 0)
                .findFirst()
                .orElseThrow();
        int before = stock.getQuantity();
        Order order = orderService.create(orderForm(List.of(stock), before + 1));

        assertThatThrownBy(() -> orderService.confirm(order.getId()))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(stockRepository.findLocked(stock.getWarehouse().getId(), stock.getVariant().getId())
                .orElseThrow().getQuantity()).isEqualTo(before);
        assertThat(orderRepository.findById(Objects.requireNonNull(order.getId()))
                .orElseThrow().getStatus())
                .isEqualTo(OrderStatus.NEW);
    }

    private Map<Long, Integer> quantitiesByVariant(List<StockItem> stocks) {
        return stocks.stream().collect(Collectors.toMap(
                stock -> stock.getVariant().getId(),
                stock -> stockRepository.findLocked(stock.getWarehouse().getId(), stock.getVariant().getId())
                        .orElseThrow().getQuantity()));
    }

    private OrderForm orderForm(List<StockItem> stocks, int quantity) {
        OrderForm form = new OrderForm();
        form.setRecipientName("Integration Test");
        form.setPhone("+10000000000");
        form.setDeliveryAddress("Test address");
        form.setWarehouseId(stocks.getFirst().getWarehouse().getId());
        List<OrderLineForm> lines = new ArrayList<>();
        for (StockItem stock : stocks) {
            OrderLineForm line = new OrderLineForm();
            line.setVariantId(stock.getVariant().getId());
            line.setQuantity(quantity);
            lines.add(line);
        }
        form.setLines(lines);
        return form;
    }
}

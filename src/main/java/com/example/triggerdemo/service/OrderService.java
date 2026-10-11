package com.example.triggerdemo.service;

import com.example.triggerdemo.entity.Order;
import com.example.triggerdemo.entity.OrderItem;
import com.example.triggerdemo.entity.OrderStatus;
import com.example.triggerdemo.entity.ProductVariant;
import com.example.triggerdemo.entity.Warehouse;
import com.example.triggerdemo.repository.OrderItemRepository;
import com.example.triggerdemo.repository.OrderRepository;
import com.example.triggerdemo.repository.ProductVariantRepository;
import com.example.triggerdemo.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockMovementService stockMovementService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductVariantRepository variantRepository,
            WarehouseRepository warehouseRepository,
            StockMovementService stockMovementService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockMovementService = stockMovementService;
    }

    @Transactional
    public Order create(OrderForm form) {
        if (form.getLines() == null || form.getLines().stream().noneMatch(line -> line.getVariantId() != null)) {
            throw new OrderWorkflowException("Добавьте хотя бы одну позицию.");
        }
        Map<ProductVariant, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineForm line : form.getLines()) {
            if (line.getVariantId() == null && line.getQuantity() == null) {
                continue;
            }
            if (line.getVariantId() == null || line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new OrderWorkflowException("Для каждой позиции укажите вариант и положительное количество.");
            }
            ProductVariant variant = variantRepository.findById(line.getVariantId())
                    .orElseThrow(() -> new OrderWorkflowException("Один из вариантов больше недоступен."));
            quantities.merge(variant, line.getQuantity(), Math::addExact);
        }

        Warehouse warehouse = warehouseRepository.findById(form.getWarehouseId())
                .orElseThrow(() -> new OrderWorkflowException("Выбранный склад не найден."));
        Order order = new Order(
                newOrderNumber(),
                form.getRecipientName().trim(),
                form.getPhone().trim(),
                form.getDeliveryAddress().trim(),
                OrderStatus.NEW,
                LocalDateTime.now(),
                warehouse);
        order = orderRepository.save(order);

        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<ProductVariant, Integer> entry : quantities.entrySet()) {
            items.add(new OrderItem(order, entry.getKey(), entry.getValue(),
                    entry.getKey().getProduct().getPrice()));
        }
        orderItemRepository.saveAll(items);
        return order;
    }

    @Transactional
    public void confirm(Long orderId) {
        Order order = getLockedOrder(orderId);
        requireStatus(order, OrderStatus.NEW, "Подтвердить можно только новый заказ.");
        List<OrderItem> items = orderItemRepository.findAllByOrder_IdOrderByIdAsc(orderId);
        if (items.isEmpty()) {
            throw new OrderWorkflowException("Нельзя подтвердить заказ без позиций.");
        }
        stockMovementService.deductForOrder(order, items);
        order.setStockDeducted(true);
        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);
    }

    @Transactional
    public void complete(Long orderId) {
        Order order = getLockedOrder(orderId);
        requireStatus(order, OrderStatus.PROCESSING, "Завершить можно только заказ в обработке.");
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
    }

    @Transactional
    public void cancel(Long orderId) {
        Order order = getLockedOrder(orderId);
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PROCESSING) {
            throw new OrderWorkflowException("Отменить можно только новый заказ или заказ в обработке.");
        }
        if (order.isStockDeducted()) {
            stockMovementService.returnForOrder(
                    order, orderItemRepository.findAllByOrder_IdOrderByIdAsc(orderId));
            order.setStockDeducted(false);
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<OrderItem> findItems(Long orderId) {
        return orderItemRepository.findAllByOrder_IdOrderByIdAsc(orderId);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotal(Long orderId) {
        return orderRepository.calculateTotal(orderId);
    }

    private Order getLockedOrder(Long orderId) {
        return orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
    }

    private void requireStatus(Order order, OrderStatus required, String message) {
        if (order.getStatus() != required) {
            throw new OrderWorkflowException(message);
        }
    }

    private String newOrderNumber() {
        return "ORD-" + LocalDateTime.now().format(ORDER_DATE_FORMAT) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public static class OrderForm {
        @jakarta.validation.constraints.NotBlank(message = "Укажите имя получателя")
        @jakarta.validation.constraints.Size(max = 255)
        private String recipientName;

        @jakarta.validation.constraints.NotBlank(message = "Укажите телефон")
        @jakarta.validation.constraints.Size(max = 50)
        private String phone;

        @jakarta.validation.constraints.NotBlank(message = "Укажите адрес доставки")
        @jakarta.validation.constraints.Size(max = 255)
        private String deliveryAddress;

        @jakarta.validation.constraints.NotNull(message = "Выберите склад")
        private Long warehouseId;

        private List<OrderLineForm> lines = new ArrayList<>();

        public String getRecipientName() { return recipientName; }
        public String getPhone() { return phone; }
        public String getDeliveryAddress() { return deliveryAddress; }
        public Long getWarehouseId() { return warehouseId; }
        public List<OrderLineForm> getLines() { return lines; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        public void setPhone(String phone) { this.phone = phone; }
        public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
        public void setLines(List<OrderLineForm> lines) { this.lines = lines; }
    }

    public static class OrderLineForm {
        private Long variantId;
        private Integer quantity;

        public Long getVariantId() { return variantId; }
        public Integer getQuantity() { return quantity; }
        public void setVariantId(Long variantId) { this.variantId = variantId; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}

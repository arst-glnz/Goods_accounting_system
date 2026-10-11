package com.example.triggerdemo.controller;

import com.example.triggerdemo.entity.Order;
import com.example.triggerdemo.entity.OrderItem;
import com.example.triggerdemo.entity.ProductVariant;
import com.example.triggerdemo.entity.StockItem;
import com.example.triggerdemo.entity.Warehouse;
import com.example.triggerdemo.repository.ProductVariantRepository;
import com.example.triggerdemo.repository.StockItemRepository;
import com.example.triggerdemo.repository.WarehouseRepository;
import com.example.triggerdemo.service.InsufficientStockException;
import com.example.triggerdemo.service.OrderService;
import com.example.triggerdemo.service.OrderService.OrderForm;
import com.example.triggerdemo.service.OrderService.OrderLineForm;
import com.example.triggerdemo.service.OrderWorkflowException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class OrderController {
    private final OrderService orderService;
    private final ProductVariantRepository variantRepository;
    private final StockItemRepository stockRepository;
    private final WarehouseRepository warehouseRepository;

    public OrderController(
            OrderService orderService,
            ProductVariantRepository variantRepository,
            StockItemRepository stockRepository,
            WarehouseRepository warehouseRepository) {
        this.orderService = orderService;
        this.variantRepository = variantRepository;
        this.stockRepository = stockRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @GetMapping
    public String orders(Model model) {
        List<Order> orders = orderService.findAll();
        Map<Long, List<OrderItem>> itemsByOrder = new HashMap<>();
        Map<Long, BigDecimal> totalsByOrder = new HashMap<>();
        orders.forEach(order -> {
            itemsByOrder.put(order.getId(), orderService.findItems(order.getId()));
            totalsByOrder.put(order.getId(), orderService.calculateTotal(order.getId()));
        });
        model.addAttribute("orders", orders);
        model.addAttribute("itemsByOrder", itemsByOrder);
        model.addAttribute("totalsByOrder", totalsByOrder);
        return "orders";
    }

    @GetMapping("/new")
    public String newOrder(Model model) {
        OrderForm form = new OrderForm();
        form.setLines(new ArrayList<>(List.of(new OrderLineForm(), new OrderLineForm())));
        populateOrderForm(model, form);
        return "order-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("orderForm") OrderForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateOrderForm(model, form);
            return "order-form";
        }
        try {
            Order order = orderService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Заказ " + order.getOrderNumber() + " создан. Остаток пока не списан.");
            return "redirect:/orders";
        } catch (OrderWorkflowException exception) {
            populateOrderForm(model, form);
            model.addAttribute("formError", exception.getMessage());
            return "order-form";
        }
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        performAction(() -> orderService.confirm(id), "Заказ подтверждён, остаток списан.", redirectAttributes);
        return "redirect:/orders";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        performAction(() -> orderService.complete(id), "Заказ завершён.", redirectAttributes);
        return "redirect:/orders";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        performAction(() -> orderService.cancel(id), "Заказ отменён.", redirectAttributes);
        return "redirect:/orders";
    }

    private void performAction(Runnable action, String successMessage, RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (InsufficientStockException | OrderWorkflowException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
    }

    private void populateOrderForm(Model model, OrderForm form) {
        List<ProductVariant> variants = variantRepository.findAllWithProduct();
        List<StockItem> stocks = stockRepository.findInventoryWithDetails();
        List<Warehouse> warehouses = warehouseRepository.findAll();
        if (warehouses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Склад не настроен.");
        }
        Map<Long, Integer> stockByVariant = stocks.stream().collect(Collectors.toMap(
                stock -> stock.getVariant().getId(),
                StockItem::getQuantity,
                Integer::sum));
        model.addAttribute("orderForm", form);
        model.addAttribute("variants", variants);
        model.addAttribute("stockByVariant", stockByVariant);
        model.addAttribute("warehouses", warehouses);
    }
}

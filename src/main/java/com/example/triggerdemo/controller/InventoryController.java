package com.example.triggerdemo.controller;

import com.example.triggerdemo.entity.InventoryMovement;
import com.example.triggerdemo.entity.ProductVariant;
import com.example.triggerdemo.entity.StockItem;
import com.example.triggerdemo.entity.Warehouse;
import com.example.triggerdemo.repository.InventoryMovementRepository;
import com.example.triggerdemo.repository.ProductVariantRepository;
import com.example.triggerdemo.repository.StockItemRepository;
import com.example.triggerdemo.repository.WarehouseRepository;
import com.example.triggerdemo.service.InsufficientStockException;
import com.example.triggerdemo.service.StockMovementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class InventoryController {
    private final StockItemRepository stockRepository;
    private final ProductVariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockMovementService movementService;

    public InventoryController(
            StockItemRepository stockRepository,
            ProductVariantRepository variantRepository,
            WarehouseRepository warehouseRepository,
            InventoryMovementRepository movementRepository,
            StockMovementService movementService) {
        this.stockRepository = stockRepository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.movementRepository = movementRepository;
        this.movementService = movementService;
    }

    @GetMapping
    public String inventory(Model model) {
        populateModel(model);
        return "inventory";
    }

    @PostMapping("/movements")
    public String createMovement(
            @Valid @ModelAttribute("movement") MovementForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateModel(model);
            model.addAttribute("movement", form);
            return "inventory";
        }

        try {
            if ("RECEIPT".equals(form.getType())) {
                movementService.receive(form.getVariantId(), form.getWarehouseId(),
                        form.getQuantity(), form.getNote());
                redirectAttributes.addFlashAttribute("successMessage", "Приход записан.");
            } else if ("ISSUE".equals(form.getType())) {
                movementService.issue(form.getVariantId(), form.getWarehouseId(),
                        form.getQuantity(), form.getNote());
                redirectAttributes.addFlashAttribute("successMessage", "Списание записано.");
            } else {
                result.rejectValue("type", "movement.type.invalid", "Выберите приход или списание.");
                populateModel(model);
                model.addAttribute("movement", form);
                return "inventory";
            }
        } catch (InsufficientStockException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/inventory";
    }

    private void populateModel(Model model) {
        List<StockItem> stocks = stockRepository.findInventoryWithDetails();
        List<ProductVariant> variants = variantRepository.findAllWithProduct();
        List<Warehouse> warehouses = warehouseRepository.findAll();
        List<InventoryMovement> movements = movementRepository.findTop100ByOrderByCreatedAtDescIdDesc();
        Map<Long, Integer> variantStock = stocks.stream().collect(Collectors.toMap(
                stock -> stock.getVariant().getId(),
                StockItem::getQuantity,
                Integer::sum));
        model.addAttribute("stocks", stocks);
        model.addAttribute("variants", variants);
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("movements", movements);
        model.addAttribute("variantStock", variantStock);
        model.addAttribute("movement", new MovementForm());
        if (warehouses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Склад не настроен.");
        }
    }

    public static class MovementForm {
        @NotNull(message = "Выберите вариант товара")
        private Long variantId;

        @NotNull(message = "Выберите склад")
        private Long warehouseId;

        @NotNull(message = "Укажите количество")
        @Positive(message = "Количество должно быть больше нуля")
        private Integer quantity;

        @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
        private String note;

        @NotNull
        private String type;

        public Long getVariantId() { return variantId; }
        public Long getWarehouseId() { return warehouseId; }
        public Integer getQuantity() { return quantity; }
        public String getNote() { return note; }
        public String getType() { return type; }
        public void setVariantId(Long variantId) { this.variantId = variantId; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public void setNote(String note) { this.note = note; }
        public void setType(String type) { this.type = type; }
    }
}

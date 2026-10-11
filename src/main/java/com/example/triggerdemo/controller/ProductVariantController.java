package com.example.triggerdemo.controller;

import com.example.triggerdemo.entity.Product;
import com.example.triggerdemo.entity.ProductVariant;
import com.example.triggerdemo.repository.ProductRepository;
import com.example.triggerdemo.service.ProductVariantService;
import com.example.triggerdemo.service.ProductVariantService.DuplicateVariantException;
import com.example.triggerdemo.service.ProductVariantService.VariantForm;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class ProductVariantController {
    private final ProductRepository productRepository;
    private final ProductVariantService variantService;

    public ProductVariantController(ProductRepository productRepository, ProductVariantService variantService) {
        this.productRepository = productRepository;
        this.variantService = variantService;
    }

    @GetMapping("/products/{productId}/variants")
    public String variants(@PathVariable long productId, Model model) {
        populateModel(productId, model);
        return "variants";
    }

    @PostMapping("/products/{productId}/variants")
    public String create(
            @PathVariable long productId,
            @Valid @ModelAttribute("variantForm") VariantForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateModel(productId, model);
            model.addAttribute("variantForm", form);
            return "variants";
        }
        try {
            variantService.create(productId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Вариант добавлен.");
        } catch (DuplicateVariantException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/products/" + productId + "/variants";
    }

    @PostMapping("/products/{productId}/variants/{variantId}")
    public String update(
            @PathVariable long productId,
            @PathVariable long variantId,
            @Valid @ModelAttribute("variantForm") VariantForm form,
            BindingResult result,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Проверьте длину параметров варианта.");
            return "redirect:/products/" + productId + "/variants";
        }
        try {
            variantService.update(productId, variantId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Вариант обновлён.");
        } catch (DuplicateVariantException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/products/" + productId + "/variants";
    }

    private void populateModel(long productId, Model model) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));
        List<ProductVariant> variants = variantService.findByProductId(productId);
        model.addAttribute("product", product);
        model.addAttribute("variants", variants);
        model.addAttribute("variantForm", new VariantForm());
    }
}

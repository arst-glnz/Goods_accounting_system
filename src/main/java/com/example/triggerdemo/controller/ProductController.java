package com.example.triggerdemo.controller;

import com.example.triggerdemo.entity.Product;
import com.example.triggerdemo.repository.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProductController {
    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("products", productRepository.findAll());
        return "products";
    }

    @GetMapping("/products/new")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String newProduct(Model model) {
        model.addAttribute("product", new Product());
        return "product-form";
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String saveProduct(@Valid @ModelAttribute Product product, BindingResult result) {
        if (result.hasErrors()) return "product-form";
        productRepository.save(product); //сохраняет новый товар
        return "redirect:/";
    }

    @GetMapping("/products/{id}/edit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String editProduct(@PathVariable Long id, Model model) {
        model.addAttribute("product", productRepository.findById(id).orElseThrow());
        return "product-form";
    }

    @PostMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String updateProduct(@PathVariable Long id, @Valid @ModelAttribute Product product,
                                BindingResult result) {
        if (result.hasErrors()) return "product-form";
        product.setId(id);
        productRepository.save(product);
        return "redirect:/";
    }

    @PostMapping("/products/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteProduct(@PathVariable Long id) {
        productRepository.deleteById(id);
        return "redirect:/";
    }
}

package com.example.triggerdemo.controller;

import com.example.triggerdemo.entity.Product;
import com.example.triggerdemo.repository.ProductRepository;
import com.example.triggerdemo.service.ProductCatalogService;
import com.example.triggerdemo.service.ProductCatalogService.ProductDeletionBlockedException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 25, 50);
    private static final List<String> ALLOWED_SORT_FIELDS = List.of("name", "price", "stockQuantity");
    private static final List<String> ALLOWED_STOCK_FILTERS = List.of("all", "available", "low", "out");

    private final ProductRepository productRepository;
    private final ProductCatalogService productCatalogService;

    public ProductController(ProductRepository productRepository, ProductCatalogService productCatalogService) {
        this.productRepository = productRepository;
        this.productCatalogService = productCatalogService;
    }

    @GetMapping("/")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String index(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String stockFilter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            Model model) {
        String search = q == null ? "" : q.trim();
        String safeFilter = ALLOWED_STOCK_FILTERS.contains(stockFilter) ? stockFilter : "all";
        String safeSort = ALLOWED_SORT_FIELDS.contains(sort) ? sort : "name";
        Sort.Direction safeDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        int safePage = Math.max(page, 0);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : DEFAULT_PAGE_SIZE;
        Page<Product> catalog = productRepository.search(
                search,
                safeFilter,
                PageRequest.of(safePage, safeSize, Sort.by(safeDirection, safeSort)));
        List<Product> allProducts = productRepository.findAll();
        model.addAttribute("products", catalog.getContent());
        model.addAttribute("catalog", catalog);
        model.addAttribute("search", search);
        model.addAttribute("stockFilter", safeFilter);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("pageSizes", ALLOWED_PAGE_SIZES);
        model.addAttribute("sort", safeSort);
        model.addAttribute("direction", safeDirection.name().toLowerCase(Locale.ROOT));
        addInventoryStatistics(model, allProducts);
        return "products";
    }

    @GetMapping("/products/export.csv")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String stockFilter,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        String search = q == null ? "" : q.trim();
        String safeFilter = ALLOWED_STOCK_FILTERS.contains(stockFilter) ? stockFilter : "all";
        String safeSort = ALLOWED_SORT_FIELDS.contains(sort) ? sort : "name";
        Sort.Direction safeDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = Pageable.unpaged(Sort.by(safeDirection, safeSort));
        List<Product> products = productRepository.search(search, safeFilter, pageable).getContent();

        StringBuilder csv = new StringBuilder("\uFEFFНазвание;Цена;Количество;Статус\r\n");
        for (Product product : products) {
            csv.append(csvCell(product.getName())).append(';')
                    .append(product.getPrice().toPlainString()).append(';')
                    .append(product.getStockQuantity()).append(';')
                    .append(product.getStockQuantity() == 0 ? "Нет на складе"
                            : product.getStockQuantity() <= 5 ? "Мало" : "В наличии")
                    .append("\r\n");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("products.csv").build());
        return ResponseEntity.ok().headers(headers).body(csv.toString().getBytes(StandardCharsets.UTF_8));
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
        product.setId(null);
        product.setStockQuantity(0);
        productCatalogService.create(product);
        return "redirect:/";
    }

    @GetMapping("/products/{id}/edit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String editProduct(@PathVariable @NonNull Long id, Model model) {
        model.addAttribute("product", findProduct(id));
        return "product-form";
    }

    @PostMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String updateProduct(@PathVariable @NonNull Long id, @Valid @ModelAttribute Product product,
                                BindingResult result) {
        if (result.hasErrors()) {
            product.setId(id);
            return "product-form";
        }
        Product existing = findProduct(id);
        existing.setName(product.getName());
        existing.setPrice(product.getPrice());
        productRepository.save(existing);
        return "redirect:/";
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public String analytics(Model model) {
        List<Product> products = productRepository.findAll();
        addInventoryStatistics(model, products);
        model.addAttribute("lowStockProducts", products.stream()
                .filter(product -> product.getStockQuantity() > 0
                        && product.getStockQuantity() <= 5)
                .toList());
        return "analytics";
    }

    @PostMapping("/products/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteProduct(@PathVariable @NonNull Long id, RedirectAttributes redirectAttributes) {
        try {
            productCatalogService.deleteIfUnused(id);
        } catch (ProductDeletionBlockedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/";
    }

    private Product findProduct(@NonNull Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    }

    private String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void addInventoryStatistics(Model model, List<Product> products) {
        long totalUnits = 0;
        long lowStockCount = 0;
        long outOfStockCount = 0;
        BigDecimal inventoryValue = BigDecimal.ZERO;
        for (Product product : products) {
            int quantity = product.getStockQuantity();
            totalUnits += quantity;
            inventoryValue = inventoryValue.add(
                    product.getPrice().multiply(BigDecimal.valueOf(quantity)));
            if (quantity == 0) {
                outOfStockCount++;
            } else if (quantity <= 5) {
                lowStockCount++;
            }
        }
        model.addAttribute("productCount", products.size());
        model.addAttribute("totalUnits", totalUnits);
        model.addAttribute("inventoryValue", inventoryValue);
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("outOfStockCount", outOfStockCount);
    }
}

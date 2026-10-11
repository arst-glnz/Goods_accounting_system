package com.example.triggerdemo.controller;

import com.example.triggerdemo.repository.AuditLogRepository;
import com.example.triggerdemo.repository.ProductCreationLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuditController {
    private final AuditLogRepository auditLogRepository;
    private final ProductCreationLogRepository productCreationLogRepository;

    public AuditController(AuditLogRepository auditLogRepository,
                           ProductCreationLogRepository productCreationLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.productCreationLogRepository = productCreationLogRepository;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public String audit(Model model) {
        model.addAttribute("logs", auditLogRepository.findAllByOrderByChangedAtDesc());
        return "audit";
    }

    @GetMapping("/creation-log")
    @PreAuthorize("hasRole('ADMIN')")
    public String creationLog(Model model) {
        model.addAttribute("logs",
                productCreationLogRepository.findAllByOrderByCreatedAtDesc());
        return "creation-log";
    }
}

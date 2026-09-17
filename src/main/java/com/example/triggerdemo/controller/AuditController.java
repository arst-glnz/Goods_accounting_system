package com.example.triggerdemo.controller;

import com.example.triggerdemo.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuditController {
    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public String audit(Model model) {
        model.addAttribute("logs", auditLogRepository.findAllByOrderByChangedAtDesc());
        return "audit";
    }
}

package com.example.triggerdemo.controller;

import com.example.triggerdemo.service.FaqService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faq")
public class FaqController {
    private final FaqService faqService;

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Map<String, String> answer(@Valid @RequestBody FaqQuestion request) {
        return Map.of("answer", faqService.answer(request.question()));
    }

    public record FaqQuestion(@NotBlank(message = "Введите вопрос") String question) {}
}

package com.example.triggerdemo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FaqServiceTest {
    private final FaqService faqService = new FaqService();

    @Test
    void answersQuestionsAboutProductOperationsAndPermissions() {
        assertThat(faqService.answer("Как добавить товар?")).contains("Добавить товар");
        assertThat(faqService.answer("Как изменить товар?")).contains("Изменить");
        assertThat(faqService.answer("Как удалить товар?")).contains("ADMIN");
        assertThat(faqService.answer("Кто может удалить товар?")).contains("только");
        assertThat(faqService.answer("Какие есть роли?")).contains("ADMIN");
    }

    @Test
    void answersQuestionsAboutAuditAndLowStock() {
        assertThat(faqService.answer("Что такое аудит?")).contains("журнал изменения цен");
        assertThat(faqService.answer("Как посмотреть журнал?")).contains("Аудит");
        assertThat(faqService.answer("Что делать, если мало товара?")).contains("от 1 до 5");
    }

    @Test
    void answersQuestionsAboutWarehouseVariantsAndOrders() {
        assertThat(faqService.answer("Как принять приход товара?")).contains("страницу «Склад»");
        assertThat(faqService.answer("Как управлять вариантами?")).contains("«Варианты»");
        assertThat(faqService.answer("Как создать заказ?")).contains("одну или несколько позиций");
        assertThat(faqService.answer("Что происходит при подтверждении заказа?"))
                .contains("спишет остатки");
        assertThat(faqService.answer("Как отменить заказ?")).contains("возвращаются на склад");
    }

    @Test
    void returnsFallbackForUnrecognizedQuestion() {
        assertThat(faqService.answer("Какая сегодня погода?")).startsWith("Я пока не знаю");
    }
}

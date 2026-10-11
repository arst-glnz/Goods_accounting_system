package com.example.triggerdemo.service;

public class OrderWorkflowException extends RuntimeException {
    public OrderWorkflowException(String message) {
        super(message);
    }
}

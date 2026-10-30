package com.proxyai.exception;

public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) { super(message); }
}

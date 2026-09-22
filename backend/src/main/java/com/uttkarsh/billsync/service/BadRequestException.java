package com.uttkarsh.billsync.service;

/** The request is well-formed but breaks a business rule. Surfaces as an HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

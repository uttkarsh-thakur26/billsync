package com.uttkarsh.billsync.service;

/** The requested resource does not exist. Surfaces as an HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

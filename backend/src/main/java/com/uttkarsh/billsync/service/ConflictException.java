package com.uttkarsh.billsync.service;

/** The request clashes with something that already exists. Surfaces as an HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

package com.uttkarsh.billsync.service;

/** The client asked for a split that cannot be honoured. Surfaces as an HTTP 400. */
public class InvalidSplitException extends RuntimeException {

    public InvalidSplitException(String message) {
        super(message);
    }
}

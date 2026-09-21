package com.uttkarsh.billsync.domain;

/**
 * How an expense is divided among its participants.
 * Stored as a VARCHAR(20) via {@code @Enumerated(EnumType.STRING)} so the
 * database stays readable and reordering the constants can never corrupt data.
 */
public enum SplitType {
    EQUAL,
    EXACT,
    PERCENTAGE
}

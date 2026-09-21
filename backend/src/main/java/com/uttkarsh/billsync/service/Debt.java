package com.uttkarsh.billsync.service;

import java.math.BigDecimal;

/**
 * One raw IOU inside a group: {@code fromUserId} owes {@code toUserId} {@code amount}.
 *
 * <p>An expense share where the participant is not the payer is a debt from the
 * participant to the payer. A recorded settlement from A to B is a debt from B to A,
 * because it cancels that much of what A owed. Everything the balance and settlement
 * code needs is expressible as a list of these.
 */
public record Debt(Long fromUserId, Long toUserId, BigDecimal amount) {
}

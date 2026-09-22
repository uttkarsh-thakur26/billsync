package com.uttkarsh.billsync.dto;

import com.uttkarsh.billsync.service.SettlementPlanner.SettlementPlan;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param transactionCount      payments in the simplified plan
 * @param naiveTransactionCount payments it would take to settle every debt directly
 */
public record SettlementPlanResponse(
        Long groupId,
        int transactionCount,
        int naiveTransactionCount,
        List<TransactionResponse> transactions) {

    public record TransactionResponse(Long fromUserId, Long toUserId, BigDecimal amount) {
    }

    public static SettlementPlanResponse from(Long groupId, SettlementPlan plan) {
        return new SettlementPlanResponse(
                groupId,
                plan.transactionCount(),
                plan.naiveTransactionCount(),
                plan.transactions().stream()
                        .map(t -> new TransactionResponse(t.fromUserId(), t.toUserId(), t.amount()))
                        .toList());
    }
}

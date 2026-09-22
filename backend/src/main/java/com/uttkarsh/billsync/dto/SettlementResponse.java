package com.uttkarsh.billsync.dto;

import com.uttkarsh.billsync.domain.Settlement;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementResponse(
        Long id,
        Long groupId,
        Long fromUserId,
        Long toUserId,
        BigDecimal amount,
        Instant settledAt) {

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroup().getId(),
                settlement.getFromUser().getId(),
                settlement.getToUser().getId(),
                settlement.getAmount(),
                settlement.getSettledAt());
    }
}

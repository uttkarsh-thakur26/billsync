package com.uttkarsh.billsync.dto;

import com.uttkarsh.billsync.domain.Expense;
import com.uttkarsh.billsync.domain.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record ExpenseResponse(
        Long id,
        Long groupId,
        Long paidByUserId,
        BigDecimal amount,
        String description,
        SplitType splitType,
        List<ShareResponse> shares,
        Instant createdAt) {

    public record ShareResponse(Long userId, BigDecimal amountOwed) {
    }

    public static ExpenseResponse from(Expense expense) {
        List<ShareResponse> shares = expense.getShares().stream()
                .map(share -> new ShareResponse(share.getUser().getId(), share.getAmountOwed()))
                .sorted(Comparator.comparing(ShareResponse::userId))
                .toList();
        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getPaidBy().getId(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getSplitType(),
                shares,
                expense.getCreatedAt());
    }
}

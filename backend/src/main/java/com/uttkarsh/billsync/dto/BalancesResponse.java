package com.uttkarsh.billsync.dto;

import com.uttkarsh.billsync.service.BalanceService.MemberBalance;

import java.math.BigDecimal;
import java.util.List;

/** Net position of every member. Positive: the group owes them. Negative: they owe the group. */
public record BalancesResponse(Long groupId, List<MemberBalanceResponse> balances) {

    public record MemberBalanceResponse(Long userId, BigDecimal balance) {
    }

    public static BalancesResponse from(Long groupId, List<MemberBalance> balances) {
        return new BalancesResponse(groupId, balances.stream()
                .map(b -> new MemberBalanceResponse(b.userId(), b.balance()))
                .toList());
    }
}

package com.uttkarsh.billsync.dto;

import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.GroupMember;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record GroupResponse(Long id, String name, Instant createdAt, List<UserResponse> members) {

    public static GroupResponse from(ExpenseGroup group) {
        List<UserResponse> members = group.getMembers().stream()
                .map(GroupMember::getUser)
                .map(UserResponse::from)
                .sorted(Comparator.comparing(UserResponse::id))
                .toList();
        return new GroupResponse(group.getId(), group.getName(), group.getCreatedAt(), members);
    }
}

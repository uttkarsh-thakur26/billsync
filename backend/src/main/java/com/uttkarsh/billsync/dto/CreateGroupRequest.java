package com.uttkarsh.billsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** @param memberUserIds optional initial members, so the UI can create a group and join it in one call */
public record CreateGroupRequest(
        @NotBlank @Size(max = 120) String name,
        List<@NotNull Long> memberUserIds) {

    public CreateGroupRequest {
        memberUserIds = memberUserIds == null ? List.of() : memberUserIds;
    }
}

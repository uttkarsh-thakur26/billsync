package com.uttkarsh.billsync.dto;

import com.uttkarsh.billsync.domain.SplitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @param participantUserIds who shares the expense; for EQUAL that is all that is needed
 * @param splitValues        for EXACT the amount each participant owes, for PERCENTAGE their
 *                           percentage; keyed by user id and must cover exactly the participants
 */
public record CreateExpenseRequest(
        @NotNull Long paidByUserId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 255) String description,
        @NotNull SplitType splitType,
        @NotEmpty List<@NotNull Long> participantUserIds,
        Map<@NotNull Long, @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal> splitValues) {
}

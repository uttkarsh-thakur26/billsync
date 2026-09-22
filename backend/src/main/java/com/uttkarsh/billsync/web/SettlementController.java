package com.uttkarsh.billsync.web;

import com.uttkarsh.billsync.dto.RecordSettlementRequest;
import com.uttkarsh.billsync.dto.SettlementPlanResponse;
import com.uttkarsh.billsync.dto.SettlementResponse;
import com.uttkarsh.billsync.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
@Tag(name = "Settlements")
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/settlement-plan")
    @Operation(summary = "The simplified list of payments that clears the group")
    public SettlementPlanResponse plan(@PathVariable Long groupId) {
        return settlementService.plan(groupId);
    }

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a payment between two members")
    public SettlementResponse record(@PathVariable Long groupId, @Valid @RequestBody RecordSettlementRequest request) {
        return settlementService.record(groupId, request);
    }
}

package com.uttkarsh.billsync.web;

import com.uttkarsh.billsync.dto.RecordSettlementRequest;
import com.uttkarsh.billsync.dto.SettlementPlanResponse;
import com.uttkarsh.billsync.dto.SettlementPlanResponse.TransactionResponse;
import com.uttkarsh.billsync.dto.SettlementResponse;
import com.uttkarsh.billsync.service.BadRequestException;
import com.uttkarsh.billsync.service.NotFoundException;
import com.uttkarsh.billsync.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
class SettlementControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SettlementService settlementService;

    @Test
    void returnsPlanWithBothCounts() throws Exception {
        given(settlementService.plan(1L)).willReturn(new SettlementPlanResponse(1L, 2, 5, List.of(
                new TransactionResponse(1L, 3L, new BigDecimal("66.67")),
                new TransactionResponse(2L, 3L, new BigDecimal("33.33")))));

        mvc.perform(get("/api/groups/1/settlement-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(1))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.naiveTransactionCount").value(5))
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].fromUserId").value(1))
                .andExpect(jsonPath("$.transactions[0].toUserId").value(3))
                .andExpect(jsonPath("$.transactions[0].amount").value("66.67"))
                .andExpect(jsonPath("$.transactions[1].amount").value("33.33"));
    }

    @Test
    void settledGroupHasEmptyTransactionList() throws Exception {
        given(settlementService.plan(1L)).willReturn(new SettlementPlanResponse(1L, 0, 0, List.of()));

        mvc.perform(get("/api/groups/1/settlement-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.transactions", hasSize(0)));
    }

    @Test
    void unknownGroupIs404() throws Exception {
        given(settlementService.plan(99L)).willThrow(new NotFoundException("Group 99 not found"));

        mvc.perform(get("/api/groups/99/settlement-plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.details[0]").value("Group 99 not found"));
    }

    @Test
    void recordsSettlement() throws Exception {
        given(settlementService.record(eq(1L), any())).willReturn(
                new SettlementResponse(5L, 1L, 2L, 3L, new BigDecimal("33.33"), Instant.parse("2026-09-21T20:15:00Z")));

        mvc.perform(post("/api/groups/1/settlements").contentType(MediaType.APPLICATION_JSON).content("""
                { "fromUserId": 2, "toUserId": 3, "amount": "33.33" }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.fromUserId").value(2))
                .andExpect(jsonPath("$.toUserId").value(3))
                .andExpect(jsonPath("$.amount").value("33.33"))
                .andExpect(jsonPath("$.settledAt").value("2026-09-21T20:15:00Z"));

        ArgumentCaptor<RecordSettlementRequest> captor = ArgumentCaptor.forClass(RecordSettlementRequest.class);
        verify(settlementService).record(eq(1L), captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("33.33");
    }

    @Test
    void rejectsEmptySettlementBody() throws Exception {
        mvc.perform(post("/api/groups/1/settlements").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasSize(3)))
                .andExpect(jsonPath("$.details", hasItem(containsString("fromUserId"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("toUserId"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));

        verifyNoInteractions(settlementService);
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        mvc.perform(post("/api/groups/1/settlements").contentType(MediaType.APPLICATION_JSON).content("""
                { "fromUserId": 2, "toUserId": 3, "amount": "0.00" }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));

        verifyNoInteractions(settlementService);
    }

    @Test
    void surfacesBusinessRuleViolationsAs400() throws Exception {
        given(settlementService.record(eq(1L), any()))
                .willThrow(new BadRequestException("A settlement needs two different people"));

        mvc.perform(post("/api/groups/1/settlements").contentType(MediaType.APPLICATION_JSON).content("""
                { "fromUserId": 2, "toUserId": 2, "amount": "10.00" }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("A settlement needs two different people"));
    }
}

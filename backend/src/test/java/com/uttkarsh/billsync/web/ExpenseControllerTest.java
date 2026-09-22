package com.uttkarsh.billsync.web;

import com.uttkarsh.billsync.domain.SplitType;
import com.uttkarsh.billsync.dto.CreateExpenseRequest;
import com.uttkarsh.billsync.dto.ExpenseResponse;
import com.uttkarsh.billsync.dto.ExpenseResponse.ShareResponse;
import com.uttkarsh.billsync.service.ExpenseService;
import com.uttkarsh.billsync.service.InvalidSplitException;
import com.uttkarsh.billsync.service.NotFoundException;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice only: the controller, the advice, the JSON mapping and CORS config are
 * real; the service is a mock. What is under test is the HTTP contract.
 */
@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ExpenseService expenseService;

    private static final String DINNER_JSON = """
            {
              "paidByUserId": 3,
              "amount": "100.00",
              "description": "Dinner at Truffles",
              "splitType": "EQUAL",
              "participantUserIds": [1, 2, 3]
            }
            """;

    private static ExpenseResponse dinner() {
        return new ExpenseResponse(42L, 1L, 3L, new BigDecimal("100.00"), "Dinner at Truffles", SplitType.EQUAL,
                List.of(new ShareResponse(1L, new BigDecimal("33.34")),
                        new ShareResponse(2L, new BigDecimal("33.33")),
                        new ShareResponse(3L, new BigDecimal("33.33"))),
                Instant.parse("2026-09-21T20:15:00Z"));
    }

    @Test
    void createsExpenseAndRendersMoneyAsStrings() throws Exception {
        given(expenseService.add(eq(1L), any())).willReturn(dinner());

        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON).content(DINNER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.groupId").value(1))
                .andExpect(jsonPath("$.paidByUserId").value(3))
                .andExpect(jsonPath("$.amount").value("100.00"))
                .andExpect(jsonPath("$.splitType").value("EQUAL"))
                .andExpect(jsonPath("$.shares", hasSize(3)))
                .andExpect(jsonPath("$.shares[0].userId").value(1))
                .andExpect(jsonPath("$.shares[0].amountOwed").value("33.34"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-21T20:15:00Z"));

        ArgumentCaptor<CreateExpenseRequest> captor = ArgumentCaptor.forClass(CreateExpenseRequest.class);
        verify(expenseService).add(eq(1L), captor.capture());
        CreateExpenseRequest sent = captor.getValue();
        assertThat(sent.amount()).isEqualByComparingTo("100.00");
        assertThat(sent.paidByUserId()).isEqualTo(3L);
        assertThat(sent.participantUserIds()).containsExactly(1L, 2L, 3L);
        assertThat(sent.splitType()).isEqualTo(SplitType.EQUAL);
    }

    @Test
    void parsesSplitValuesKeyedByUserId() throws Exception {
        given(expenseService.add(eq(1L), any())).willReturn(dinner());

        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "paidByUserId": 1,
                  "amount": "100.00",
                  "description": "Cab",
                  "splitType": "EXACT",
                  "participantUserIds": [1, 2],
                  "splitValues": { "1": "70.00", "2": "30.00" }
                }
                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateExpenseRequest> captor = ArgumentCaptor.forClass(CreateExpenseRequest.class);
        verify(expenseService).add(eq(1L), captor.capture());
        assertThat(captor.getValue().splitValues())
                .containsEntry(1L, new BigDecimal("70.00"))
                .containsEntry(2L, new BigDecimal("30.00"));
    }

    @Test
    void rejectsInvalidBodyWithTheStandardErrorShape() throws Exception {
        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "amount": "0",
                  "description": "   ",
                  "splitType": "EQUAL",
                  "participantUserIds": []
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasSize(4)))
                .andExpect(jsonPath("$.details", hasItem(containsString("amount"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("description"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("paidByUserId"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("participantUserIds"))));

        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsAmountWithThreeDecimals() throws Exception {
        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content(DINNER_JSON.replace("\"100.00\"", "\"100.001\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));

        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsUnknownSplitTypeAsMalformed() throws Exception {
        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content(DINNER_JSON.replace("EQUAL", "HALVES")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request"))
                .andExpect(jsonPath("$.details[0]", containsString("EQUAL")));
    }

    @Test
    void surfacesSplitRuleViolationsAs400() throws Exception {
        String message = "Exact split amounts must sum to the expense total: amounts sum to 99.99 but the expense is 100.00";
        given(expenseService.add(eq(1L), any())).willThrow(new InvalidSplitException(message));

        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON).content(DINNER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(message));
    }

    @Test
    void unknownGroupIs404() throws Exception {
        given(expenseService.add(eq(99L), any())).willThrow(new NotFoundException("Group 99 not found"));

        mvc.perform(post("/api/groups/99/expenses").contentType(MediaType.APPLICATION_JSON).content(DINNER_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.details[0]").value("Group 99 not found"));
    }

    @Test
    void nonNumericIdIs400NotStackTrace() throws Exception {
        mvc.perform(get("/api/groups/abc/expenses"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]", containsString("groupId")));
    }

    @Test
    void listsExpenses() throws Exception {
        given(expenseService.list(1L)).willReturn(List.of(dinner()));

        mvc.perform(get("/api/groups/1/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Dinner at Truffles"))
                .andExpect(jsonPath("$[0].shares[1].amountOwed").value("33.33"));
    }

    @Test
    void deletesExpenseWithNoContent() throws Exception {
        mvc.perform(delete("/api/expenses/7"))
                .andExpect(status().isNoContent());

        verify(expenseService).delete(7L);
    }

    @Test
    void deletingUnknownExpenseIs404() throws Exception {
        willThrow(new NotFoundException("Expense 7 not found")).given(expenseService).delete(7L);

        mvc.perform(delete("/api/expenses/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details[0]").value("Expense 7 not found"));
    }

    @Test
    void corsPreflightAllowsTheViteDevServer() throws Exception {
        mvc.perform(options("/api/groups/1/expenses")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsPreflightRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/groups/1/expenses")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

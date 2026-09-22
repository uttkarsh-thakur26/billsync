package com.uttkarsh.billsync.web;

import com.uttkarsh.billsync.dto.CreateExpenseRequest;
import com.uttkarsh.billsync.dto.ExpenseResponse;
import com.uttkarsh.billsync.dto.UpdateExpenseRequest;
import com.uttkarsh.billsync.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping("/groups/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an expense to a group")
    public ExpenseResponse add(@PathVariable Long groupId, @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.add(groupId, request);
    }

    @GetMapping("/groups/{groupId}/expenses")
    @Operation(summary = "List a group's expenses, newest first")
    public List<ExpenseResponse> list(@PathVariable Long groupId) {
        return expenseService.list(groupId);
    }

    @PatchMapping("/expenses/{id}")
    @Operation(summary = "Change an expense's description")
    public ExpenseResponse rename(@PathVariable Long id, @Valid @RequestBody UpdateExpenseRequest request) {
        return expenseService.rename(id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an expense")
    public void delete(@PathVariable Long id) {
        expenseService.delete(id);
    }
}

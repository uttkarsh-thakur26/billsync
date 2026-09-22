package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.Expense;
import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.SplitType;
import com.uttkarsh.billsync.dto.CreateExpenseRequest;
import com.uttkarsh.billsync.dto.ExpenseResponse;
import com.uttkarsh.billsync.repository.ExpenseGroupRepository;
import com.uttkarsh.billsync.repository.ExpenseRepository;
import com.uttkarsh.billsync.repository.GroupMemberRepository;
import com.uttkarsh.billsync.repository.UserRepository;
import com.uttkarsh.billsync.service.SplitCalculator.Allocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final UserRepository users;
    private final SplitCalculator splitCalculator;

    public ExpenseResponse add(Long groupId, CreateExpenseRequest request) {
        ExpenseGroup group = groups.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group " + groupId + " not found"));
        Set<Long> memberIds = new HashSet<>(members.findUserIdsByGroupId(groupId));
        if (!memberIds.contains(request.paidByUserId())) {
            throw new BadRequestException("Payer " + request.paidByUserId() + " is not a member of group " + groupId);
        }
        List<Long> outsiders = request.participantUserIds().stream()
                .filter(id -> !memberIds.contains(id))
                .distinct()
                .toList();
        if (!outsiders.isEmpty()) {
            throw new BadRequestException("Participants " + outsiders + " are not members of group " + groupId);
        }

        List<Allocation> allocations = switch (request.splitType()) {
            case EQUAL -> splitCalculator.splitEqually(request.amount(), request.participantUserIds());
            case EXACT -> splitCalculator.splitExactly(request.amount(), splitValuesFor(request));
            case PERCENTAGE -> splitCalculator.splitByPercentage(request.amount(), splitValuesFor(request));
        };

        // getReferenceById hands back a proxy holding only the id: enough to set the
        // foreign keys without a SELECT per user. Membership was already verified above.
        Expense expense = new Expense(
                group,
                users.getReferenceById(request.paidByUserId()),
                request.amount().setScale(2),
                request.description().trim(),
                request.splitType());
        for (Allocation allocation : allocations) {
            expense.addShare(users.getReferenceById(allocation.userId()), allocation.amount());
        }
        return ExpenseResponse.from(expenses.save(expense));
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(Long groupId) {
        if (!groups.existsById(groupId)) {
            throw new NotFoundException("Group " + groupId + " not found");
        }
        return expenses.findByGroupIdOrderByCreatedAtDescIdDesc(groupId).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    public void delete(Long expenseId) {
        Expense expense = expenses.findById(expenseId)
                .orElseThrow(() -> new NotFoundException("Expense " + expenseId + " not found"));
        expenses.delete(expense);
    }

    /** EXACT and PERCENTAGE need one value per participant, no more and no fewer. */
    private static Map<Long, BigDecimal> splitValuesFor(CreateExpenseRequest request) {
        String unit = request.splitType() == SplitType.EXACT ? "amount" : "percentage";
        if (request.splitValues() == null || request.splitValues().isEmpty()) {
            throw new BadRequestException(request.splitType() + " split requires splitValues: one " + unit + " per participant");
        }
        Set<Long> participants = new HashSet<>(request.participantUserIds());
        if (!request.splitValues().keySet().equals(participants)) {
            throw new BadRequestException("splitValues must contain exactly one " + unit + " for each participant "
                    + participants.stream().sorted().toList() + " but has entries for "
                    + request.splitValues().keySet().stream().sorted().toList());
        }
        return request.splitValues();
    }
}

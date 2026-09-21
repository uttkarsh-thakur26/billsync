package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.Expense;
import com.uttkarsh.billsync.domain.ExpenseShare;
import com.uttkarsh.billsync.domain.GroupMember;
import com.uttkarsh.billsync.domain.Settlement;
import com.uttkarsh.billsync.repository.ExpenseGroupRepository;
import com.uttkarsh.billsync.repository.ExpenseRepository;
import com.uttkarsh.billsync.repository.GroupMemberRepository;
import com.uttkarsh.billsync.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Works out where every member of a group stands.
 *
 * <p>A member's net balance is everything they paid, minus everything they owe,
 * adjusted for settlements already made. Positive means the group owes them;
 * negative means they owe the group. Across a group the balances always sum to
 * zero, because every paisa someone owes is a paisa someone else is owed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public record MemberBalance(Long userId, BigDecimal balance) {
    }

    /** Net balance for every member of the group, in ascending user-id order, zeros included. */
    public List<MemberBalance> balancesFor(Long groupId) {
        requireGroup(groupId);
        List<Long> memberIds = memberRepository.findByGroupId(groupId).stream()
                .map(GroupMember::getUser)
                .map(user -> user.getId())
                .toList();
        return netBalances(memberIds, debtsFor(groupId)).entrySet().stream()
                .map(entry -> new MemberBalance(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Every raw debt in the group, one per share owed to somebody else plus one
     * reversed entry per settlement. This runs three queries regardless of group
     * size: members are not needed, expenses come with their shares via an entity
     * graph, and reading an id off a lazy {@code paidBy} or {@code user} proxy does
     * not trigger a load.
     */
    public List<Debt> debtsFor(Long groupId) {
        requireGroup(groupId);
        List<Debt> debts = new ArrayList<>();
        for (Expense expense : expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId)) {
            Long payerId = expense.getPaidBy().getId();
            for (ExpenseShare share : expense.getShares()) {
                Long participantId = share.getUser().getId();
                if (!participantId.equals(payerId)) {
                    debts.add(new Debt(participantId, payerId, share.getAmountOwed()));
                }
            }
        }
        for (Settlement settlement : settlementRepository.findByGroupId(groupId)) {
            // A paying B is the same as B now owing A that much: a debt the other way.
            debts.add(new Debt(settlement.getToUser().getId(), settlement.getFromUser().getId(), settlement.getAmount()));
        }
        return debts;
    }

    /**
     * Folds raw debts into a net balance per user. Pure: no database, no Spring.
     * Every listed member gets an entry even with no activity; anyone who appears
     * in a debt but not in {@code memberIds} is added as well.
     */
    public static Map<Long, BigDecimal> netBalances(Collection<Long> memberIds, List<Debt> debts) {
        Map<Long, BigDecimal> balances = new TreeMap<>();
        for (Long id : memberIds) {
            balances.put(id, ZERO_MONEY);
        }
        for (Debt debt : debts) {
            balances.merge(debt.fromUserId(), debt.amount().negate(), BigDecimal::add);
            balances.merge(debt.toUserId(), debt.amount(), BigDecimal::add);
        }
        return balances;
    }

    private void requireGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new NotFoundException("Group " + groupId + " not found");
        }
    }
}

package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.Settlement;
import com.uttkarsh.billsync.dto.RecordSettlementRequest;
import com.uttkarsh.billsync.dto.SettlementPlanResponse;
import com.uttkarsh.billsync.dto.SettlementResponse;
import com.uttkarsh.billsync.repository.ExpenseGroupRepository;
import com.uttkarsh.billsync.repository.GroupMemberRepository;
import com.uttkarsh.billsync.repository.SettlementRepository;
import com.uttkarsh.billsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class SettlementService {

    private final SettlementRepository settlements;
    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final UserRepository users;
    private final BalanceService balanceService;
    private final SettlementPlanner planner;

    @Transactional(readOnly = true)
    public SettlementPlanResponse plan(Long groupId) {
        return SettlementPlanResponse.from(groupId, planner.plan(balanceService.debtsFor(groupId)));
    }

    /**
     * Records that one member paid another. Any two members may be recorded, whether
     * or not the plan suggested it: people pay each other however they like, and the
     * balances simply absorb it.
     */
    public SettlementResponse record(Long groupId, RecordSettlementRequest request) {
        ExpenseGroup group = groups.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group " + groupId + " not found"));
        if (request.fromUserId().equals(request.toUserId())) {
            throw new BadRequestException("A settlement needs two different people");
        }
        Set<Long> memberIds = Set.copyOf(members.findUserIdsByGroupId(groupId));
        for (Long userId : new Long[]{request.fromUserId(), request.toUserId()}) {
            if (!memberIds.contains(userId)) {
                throw new BadRequestException("User " + userId + " is not a member of group " + groupId);
            }
        }
        Settlement settlement = new Settlement(
                group,
                users.getReferenceById(request.fromUserId()),
                users.getReferenceById(request.toUserId()),
                request.amount().setScale(2));
        return SettlementResponse.from(settlements.save(settlement));
    }
}

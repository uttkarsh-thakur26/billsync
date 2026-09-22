package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.GroupMember;
import com.uttkarsh.billsync.domain.User;
import com.uttkarsh.billsync.dto.CreateGroupRequest;
import com.uttkarsh.billsync.dto.GroupResponse;
import com.uttkarsh.billsync.repository.ExpenseGroupRepository;
import com.uttkarsh.billsync.repository.GroupMemberRepository;
import com.uttkarsh.billsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final UserRepository users;
    private final BalanceService balanceService;

    public GroupResponse create(CreateGroupRequest request) {
        ExpenseGroup group = new ExpenseGroup(request.name().trim());
        for (Long userId : new LinkedHashSet<>(request.memberUserIds())) {
            group.addMember(requireUser(userId));
        }
        return GroupResponse.from(groups.save(group));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list() {
        return groups.findAllByOrderByCreatedAtDescIdDesc().stream().map(GroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse get(Long groupId) {
        return GroupResponse.from(requireGroupWithMembers(groupId));
    }

    public GroupResponse addMember(Long groupId, Long userId) {
        ExpenseGroup group = requireGroupWithMembers(groupId);
        User user = requireUser(userId);
        if (members.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ConflictException("User " + userId + " is already a member of group " + groupId);
        }
        group.addMember(user);
        return GroupResponse.from(groups.save(group));
    }

    /**
     * A member may leave only once they are square with the group. Their expenses
     * and settlements stay: history is never rewritten, and if a later deletion
     * puts them back in debt the balance view will show them again.
     */
    public void removeMember(Long groupId, Long userId) {
        ExpenseGroup group = requireGroupWithMembers(groupId);
        GroupMember member = group.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("User " + userId + " is not a member of group " + groupId));
        BigDecimal balance = BalanceService.netBalances(List.of(userId), balanceService.debtsFor(groupId)).get(userId);
        if (balance.signum() != 0) {
            String name = member.getUser().getName();
            String position = balance.signum() < 0 ? "still owes " + balance.negate() : "is still owed " + balance;
            throw new BadRequestException(name + " " + position + " in this group. Settle up before removing them.");
        }
        // Through the parent collection, not the repository: with cascade=ALL a row deleted
        // directly would be re-persisted at flush if the group is still in the session.
        group.removeMember(member);
    }

    private ExpenseGroup requireGroupWithMembers(Long groupId) {
        return groups.findWithMembersById(groupId)
                .orElseThrow(() -> new NotFoundException("Group " + groupId + " not found"));
    }

    private User requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }
}

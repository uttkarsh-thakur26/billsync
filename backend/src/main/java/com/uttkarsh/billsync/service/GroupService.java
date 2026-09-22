package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.User;
import com.uttkarsh.billsync.dto.CreateGroupRequest;
import com.uttkarsh.billsync.dto.GroupResponse;
import com.uttkarsh.billsync.repository.ExpenseGroupRepository;
import com.uttkarsh.billsync.repository.GroupMemberRepository;
import com.uttkarsh.billsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final UserRepository users;

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

    private ExpenseGroup requireGroupWithMembers(Long groupId) {
        return groups.findWithMembersById(groupId)
                .orElseThrow(() -> new NotFoundException("Group " + groupId + " not found"));
    }

    private User requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }
}

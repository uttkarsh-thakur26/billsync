package com.uttkarsh.billsync.repository;

import com.uttkarsh.billsync.domain.GroupMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    @EntityGraph(attributePaths = "user")
    List<GroupMember> findByGroupId(Long groupId);
}

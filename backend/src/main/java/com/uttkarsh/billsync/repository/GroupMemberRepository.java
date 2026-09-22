package com.uttkarsh.billsync.repository;

import com.uttkarsh.billsync.domain.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    /** Just the ids: membership checks do not need whole User rows. */
    @Query("select m.user.id from GroupMember m where m.group.id = :groupId order by m.user.id")
    List<Long> findUserIdsByGroupId(Long groupId);
}

package com.uttkarsh.billsync.repository;

import com.uttkarsh.billsync.domain.ExpenseGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {

    /**
     * Loads a group together with its members and their users in one query.
     * Without the entity graph this would be 1 query for the group, 1 for the
     * member list and then 1 per member for each user: the N+1 problem.
     */
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<ExpenseGroup> findWithMembersById(Long id);
}

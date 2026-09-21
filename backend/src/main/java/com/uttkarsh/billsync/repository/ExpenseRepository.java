package com.uttkarsh.billsync.repository;

import com.uttkarsh.billsync.domain.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /** All expenses in a group, newest first, with their shares fetched in the same query. */
    @EntityGraph(attributePaths = "shares")
    List<Expense> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);
}

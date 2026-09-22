package com.uttkarsh.billsync.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A group of people who share expenses.
 * The table is {@code expense_groups} because GROUP is a reserved word in PostgreSQL.
 */
@Entity
@Table(name = "expense_groups")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Owned by the group: saving the group saves its memberships, deleting it deletes them. */
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupMember> members = new ArrayList<>();

    public ExpenseGroup(String name) {
        this.name = name;
    }

    public GroupMember addMember(User user) {
        GroupMember member = new GroupMember(this, user);
        members.add(member);
        return member;
    }

    /** Drops the membership; orphanRemoval deletes the row at flush. */
    public boolean removeMember(GroupMember member) {
        return members.remove(member);
    }
}

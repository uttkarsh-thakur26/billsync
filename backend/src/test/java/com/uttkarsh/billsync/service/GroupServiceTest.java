package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.Expense;
import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.Settlement;
import com.uttkarsh.billsync.domain.SplitType;
import com.uttkarsh.billsync.domain.User;
import com.uttkarsh.billsync.repository.ExpenseGroupRepository;
import com.uttkarsh.billsync.repository.ExpenseRepository;
import com.uttkarsh.billsync.repository.GroupMemberRepository;
import com.uttkarsh.billsync.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GroupService.class, BalanceService.class})
class GroupServiceTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ExpenseGroupRepository groups;
    @Autowired
    private GroupMemberRepository members;
    @Autowired
    private ExpenseRepository expenses;
    @Autowired
    private SettlementRepository settlements;
    @Autowired
    private GroupService groupService;
    @Autowired
    private BalanceService balanceService;

    private User alice;
    private User bob;
    private ExpenseGroup group;

    @BeforeEach
    void setUp() {
        alice = em.persist(new User("Alice", "alice@example.com"));
        bob = em.persist(new User("Bob", "bob@example.com"));
        group = new ExpenseGroup("Flat 4B");
        group.addMember(alice);
        group.addMember(bob);
        group = groups.save(group);
        em.flush();
        em.clear();
    }

    /** Alice pays 40 for both: Bob now owes her 20. */
    private void aliceBuysDinner() {
        Expense dinner = new Expense(group, alice, new BigDecimal("40.00"), "Dinner", SplitType.EQUAL);
        dinner.addShare(alice, new BigDecimal("20.00"));
        dinner.addShare(bob, new BigDecimal("20.00"));
        expenses.save(dinner);
        em.flush();
        em.clear();
    }

    @Test
    void removesAMemberWhoOwesNothing() {
        groupService.removeMember(group.getId(), bob.getId());
        em.flush();

        assertThat(members.existsByGroupIdAndUserId(group.getId(), bob.getId())).isFalse();
        assertThat(members.existsByGroupIdAndUserId(group.getId(), alice.getId())).isTrue();
    }

    @Test
    void refusesWhileTheyStillOwe() {
        aliceBuysDinner();

        assertThatThrownBy(() -> groupService.removeMember(group.getId(), bob.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bob still owes 20.00 in this group. Settle up before removing them.");
        assertThat(members.existsByGroupIdAndUserId(group.getId(), bob.getId())).isTrue();
    }

    @Test
    void refusesWhileTheyAreStillOwed() {
        aliceBuysDinner();

        assertThatThrownBy(() -> groupService.removeMember(group.getId(), alice.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Alice is still owed 20.00");
    }

    @Test
    void allowsLeavingOnceSettledAndKeepsTheHistory() {
        aliceBuysDinner();
        settlements.save(new Settlement(group, bob, alice, new BigDecimal("20.00")));
        em.flush();
        em.clear();

        groupService.removeMember(group.getId(), bob.getId());
        em.flush();

        assertThat(members.existsByGroupIdAndUserId(group.getId(), bob.getId())).isFalse();
        assertThat(expenses.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId())).hasSize(1);
        assertThat(settlements.findByGroupId(group.getId())).hasSize(1);
        // Bob is history now: no longer listed in balances, although his expense remains.
        assertThat(balanceService.balancesFor(group.getId()))
                .extracting(BalanceService.MemberBalance::userId)
                .containsExactly(alice.getId());
    }

    @Test
    void aFormerMemberReappearsInBalancesIfTheyEndUpOwingAgain() {
        aliceBuysDinner();
        settlements.save(new Settlement(group, bob, alice, new BigDecimal("20.00")));
        em.flush();
        em.clear();
        groupService.removeMember(group.getId(), bob.getId());
        em.flush();

        // The dinner is deleted; the 20.00 Bob paid Alice now stands alone, so Alice owes him.
        expenses.deleteAll(expenses.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId()));
        em.flush();
        em.clear();

        assertThat(balanceService.balancesFor(group.getId()))
                .extracting(BalanceService.MemberBalance::userId, BalanceService.MemberBalance::balance)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(alice.getId(), new BigDecimal("-20.00")),
                        org.assertj.core.groups.Tuple.tuple(bob.getId(), new BigDecimal("20.00")));
    }

    @Test
    void nonMemberIs404() {
        User cara = em.persist(new User("Cara", "cara@example.com"));

        assertThatThrownBy(() -> groupService.removeMember(group.getId(), cara.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void unknownGroupIs404() {
        assertThatThrownBy(() -> groupService.removeMember(999L, alice.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Group 999");
    }
}

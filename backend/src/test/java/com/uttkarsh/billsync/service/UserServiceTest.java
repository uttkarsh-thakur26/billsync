package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.Expense;
import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.SplitType;
import com.uttkarsh.billsync.domain.User;
import com.uttkarsh.billsync.dto.UpdateUserRequest;
import com.uttkarsh.billsync.repository.ExpenseGroupRepository;
import com.uttkarsh.billsync.repository.ExpenseRepository;
import com.uttkarsh.billsync.repository.UserRepository;
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
@Import(UserService.class)
class UserServiceTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private UserRepository users;
    @Autowired
    private ExpenseGroupRepository groups;
    @Autowired
    private ExpenseRepository expenses;
    @Autowired
    private UserService userService;

    @Test
    void renamesAndKeepsEmailWhenOnlyNameIsSent() {
        User alice = em.persist(new User("Alise", "alice@example.com"));

        var updated = userService.update(alice.getId(), new UpdateUserRequest("Alice", null));

        assertThat(updated.name()).isEqualTo("Alice");
        assertThat(updated.email()).isEqualTo("alice@example.com");
    }

    @Test
    void refusesBlankNameAndTakenEmail() {
        User alice = em.persist(new User("Alice", "alice@example.com"));
        em.persist(new User("Bob", "bob@example.com"));

        assertThatThrownBy(() -> userService.update(alice.getId(), new UpdateUserRequest("  ", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> userService.update(alice.getId(), new UpdateUserRequest(null, "BOB@example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deletesAPersonWithNoFootprint() {
        User stray = em.persist(new User("Typo", "typo@example.com"));

        userService.delete(stray.getId());

        assertThat(users.existsById(stray.getId())).isFalse();
    }

    @Test
    void refusesWhileInAGroup() {
        User alice = em.persist(new User("Alice", "alice@example.com"));
        ExpenseGroup group = new ExpenseGroup("Flat 4B");
        group.addMember(alice);
        groups.save(group);
        em.flush();

        assertThatThrownBy(() -> userService.delete(alice.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Alice is still in a group. Remove them from their groups first.");
    }

    @Test
    void refusesWhenNamedInAnExpenseEvenAfterLeavingTheGroup() {
        User alice = em.persist(new User("Alice", "alice@example.com"));
        User bob = em.persist(new User("Bob", "bob@example.com"));
        ExpenseGroup group = new ExpenseGroup("Flat 4B");
        group.addMember(alice);
        group.addMember(bob);
        group = groups.save(group);
        Expense dinner = new Expense(group, alice, new BigDecimal("40.00"), "Dinner", SplitType.EQUAL);
        dinner.addShare(alice, new BigDecimal("20.00"));
        dinner.addShare(bob, new BigDecimal("20.00"));
        expenses.save(dinner);
        group.removeMember(group.getMembers().stream().filter(m -> m.getUser().equals(bob)).findFirst().orElseThrow());
        em.flush();
        em.clear();

        // Bob left the group, but the dinner still says he owed 20.00: the foreign key blocks the delete.
        assertThatThrownBy(() -> userService.delete(bob.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("recorded expenses or settlements");
    }

    @Test
    void unknownUserIs404() {
        assertThatThrownBy(() -> userService.delete(999L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> userService.update(999L, new UpdateUserRequest("X", null)))
                .isInstanceOf(NotFoundException.class);
    }
}

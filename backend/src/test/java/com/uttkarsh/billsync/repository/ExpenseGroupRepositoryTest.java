package com.uttkarsh.billsync.repository;

import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.GroupMember;
import com.uttkarsh.billsync.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
// Keep the H2 URL from application-test.yml (MODE=PostgreSQL) instead of a generic embedded database.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExpenseGroupRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ExpenseGroupRepository groups;

    @Test
    void savesAndReloadsGroupWithMembers() {
        User alice = em.persist(new User("Alice", "alice@example.com"));
        User bob = em.persist(new User("Bob", "bob@example.com"));

        ExpenseGroup group = new ExpenseGroup("Goa trip");
        group.addMember(alice);
        group.addMember(bob);
        Long id = groups.save(group).getId();

        // Push the inserts to the database and forget every managed entity, so the
        // read below really hits H2 instead of returning the same objects from cache.
        em.flush();
        em.clear();

        ExpenseGroup reloaded = groups.findWithMembersById(id).orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("Goa trip");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getMembers())
                .extracting(GroupMember::getUser)
                .extracting(User::getEmail)
                .containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
        assertThat(reloaded.getMembers())
                .allSatisfy(member -> assertThat(member.getGroup().getId()).isEqualTo(id));
    }

    @Test
    void rejectsSameUserTwiceInOneGroup() {
        User alice = em.persist(new User("Alice", "alice@example.com"));

        ExpenseGroup group = new ExpenseGroup("Flat 4B");
        group.addMember(alice);
        group.addMember(alice);

        assertThatThrownBy(() -> groups.saveAndFlush(group))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.Expense;
import com.uttkarsh.billsync.domain.ExpenseGroup;
import com.uttkarsh.billsync.domain.Settlement;
import com.uttkarsh.billsync.domain.SplitType;
import com.uttkarsh.billsync.domain.User;
import com.uttkarsh.billsync.repository.ExpenseGroupRepository;
import com.uttkarsh.billsync.repository.ExpenseRepository;
import com.uttkarsh.billsync.repository.SettlementRepository;
import com.uttkarsh.billsync.service.BalanceService.MemberBalance;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceServiceTest {

    private static Debt owes(long from, long to, String amount) {
        return new Debt(from, to, new BigDecimal(amount));
    }

    private static BigDecimal sum(Map<Long, BigDecimal> balances) {
        return balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Nested
    class PureArithmetic {

        @Test
        void balancesAlwaysSumToZero() {
            // Every debt adds the same amount to one side that it removes from the other,
            // so the group as a whole can never be up or down. If this ever fails, the
            // bug is upstream in the splitter, not here.
            List<List<Debt>> ledgers = List.of(
                    List.of(),
                    List.of(owes(1, 2, "33.34"), owes(3, 2, "33.33")),
                    List.of(owes(1, 2, "200.00"), owes(2, 3, "150.00"), owes(3, 1, "80.00")),
                    List.of(owes(1, 2, "0.01"), owes(2, 1, "0.01"), owes(1, 3, "999999.99"))
            );
            for (List<Debt> debts : ledgers) {
                assertThat(sum(BalanceService.netBalances(List.of(1L, 2L, 3L), debts)))
                        .as("%s", debts).isEqualByComparingTo("0");
            }
        }

        @Test
        void membersWithNoActivityAppearWithZeroBalance() {
            Map<Long, BigDecimal> balances = BalanceService.netBalances(List.of(1L, 2L, 3L), List.of(owes(1, 2, "10.00")));

            assertThat(balances).containsExactly(
                    Map.entry(1L, new BigDecimal("-10.00")),
                    Map.entry(2L, new BigDecimal("10.00")),
                    Map.entry(3L, new BigDecimal("0.00"))
            );
        }
    }

    @DataJpaTest
    @ActiveProfiles("test")
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import({BalanceService.class, SplitCalculator.class})
    @Nested
    class AgainstTheDatabase {

        @Autowired
        private TestEntityManager em;
        @Autowired
        private ExpenseGroupRepository groups;
        @Autowired
        private ExpenseRepository expenses;
        @Autowired
        private SettlementRepository settlements;
        @Autowired
        private SplitCalculator splitCalculator;
        @Autowired
        private BalanceService balanceService;

        private Expense expense(ExpenseGroup group, User paidBy, String amount, List<User> participants) {
            Expense expense = new Expense(group, paidBy, new BigDecimal(amount), "test", SplitType.EQUAL);
            Map<Long, User> byId = participants.stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
            splitCalculator.splitEqually(new BigDecimal(amount), byId.keySet())
                    .forEach(share -> expense.addShare(byId.get(share.userId()), share.amount()));
            return expenses.save(expense);
        }

        @Test
        void foldsExpensesSharesAndSettlementsIntoNetBalances() {
            User alice = em.persist(new User("Alice", "alice@example.com"));
            User bob = em.persist(new User("Bob", "bob@example.com"));
            User cara = em.persist(new User("Cara", "cara@example.com"));
            ExpenseGroup group = new ExpenseGroup("Goa trip");
            group.addMember(alice);
            group.addMember(bob);
            group.addMember(cara);
            group = groups.save(group);

            // Alice pays 100 for all three -> shares 33.34 / 33.33 / 33.33
            expense(group, alice, "100.00", List.of(alice, bob, cara));
            // Bob pays 60 for Bob and Cara -> 30 each
            expense(group, bob, "60.00", List.of(bob, cara));
            // Cara pays Alice 20 towards what she owes
            settlements.save(new Settlement(group, cara, alice, new BigDecimal("20.00")));
            em.flush();
            em.clear();

            List<MemberBalance> balances = balanceService.balancesFor(group.getId());

            // Alice: +100 paid, -33.34 owed, -20 received  = +46.66
            // Bob:   +60 paid,  -33.33 -30 owed            =  -3.33
            // Cara:  -33.33 -30 owed, +20 paid to Alice    = -43.33
            assertThat(balances).containsExactly(
                    new MemberBalance(alice.getId(), new BigDecimal("46.66")),
                    new MemberBalance(bob.getId(), new BigDecimal("-3.33")),
                    new MemberBalance(cara.getId(), new BigDecimal("-43.33"))
            );
            assertThat(balances.stream().map(MemberBalance::balance).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("0");
        }

        @Test
        void singleMemberGroupHasOneZeroBalanceAndAnEmptyPlan() {
            User solo = em.persist(new User("Solo", "solo@example.com"));
            ExpenseGroup group = new ExpenseGroup("Just me");
            group.addMember(solo);
            group = groups.save(group);
            em.flush();
            em.clear();

            assertThat(balanceService.balancesFor(group.getId()))
                    .containsExactly(new MemberBalance(solo.getId(), new BigDecimal("0.00")));
            assertThat(new SettlementPlanner().plan(balanceService.debtsFor(group.getId())).transactions()).isEmpty();
        }

        @Test
        void unknownGroupIsRejected() {
            assertThatThrownBy(() -> balanceService.balancesFor(999L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("999");
        }
    }
}

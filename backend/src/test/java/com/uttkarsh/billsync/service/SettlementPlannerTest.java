package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.service.SettlementPlanner.SettlementPlan;
import com.uttkarsh.billsync.service.SettlementPlanner.Transfer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementPlannerTest {

    private final SettlementPlanner planner = new SettlementPlanner();

    private static Debt owes(long from, long to, String amount) {
        return new Debt(from, to, new BigDecimal(amount));
    }

    /**
     * The correctness check for any plan: after every transfer in it is paid,
     * nobody in the group is owed anything and nobody owes anything.
     */
    private static void assertPlanSettlesEverything(List<Debt> debts, SettlementPlan plan) {
        Map<Long, BigDecimal> balances = BalanceService.netBalances(List.of(), debts);
        for (Transfer t : plan.transactions()) {
            balances.merge(t.fromUserId(), t.amount(), BigDecimal::add);
            balances.merge(t.toUserId(), t.amount().negate(), BigDecimal::add);
        }
        assertThat(balances.values()).allSatisfy(b -> assertThat(b).isEqualByComparingTo("0"));
        assertThat(plan.transactions()).allSatisfy(t -> {
            assertThat(t.amount()).isPositive();
            assertThat(t.amount().scale()).isEqualTo(2);
            assertThat(t.fromUserId()).isNotEqualTo(t.toUserId());
        });
    }

    private static long nonZeroBalances(List<Debt> debts) {
        return BalanceService.netBalances(List.of(), debts).values().stream().filter(b -> b.signum() != 0).count();
    }

    @Test
    void threePersonCycleCollapsesToFewerTransactions() {
        // A owes B 200, B owes C 150, C owes A 80: three transfers if paid as-is.
        // Net: A is down 120, B is up 50, C is up 70. Two transfers clear it.
        List<Debt> debts = List.of(owes(1, 2, "200.00"), owes(2, 3, "150.00"), owes(3, 1, "80.00"));

        SettlementPlan plan = planner.plan(debts);

        assertThat(plan.naiveTransactionCount()).isEqualTo(3);
        assertThat(plan.transactionCount()).isEqualTo(2);
        assertThat(plan.transactions()).containsExactly(
                new Transfer(1L, 3L, new BigDecimal("70.00")),   // biggest creditor first
                new Transfer(1L, 2L, new BigDecimal("50.00"))
        );
        assertPlanSettlesEverything(debts, plan);
    }

    @Test
    void fivePersonGroupWithEightNaiveTransactionsNeedsAtMostFour() {
        List<Debt> debts = List.of(
                owes(1, 2, "10.00"), owes(1, 3, "20.00"), owes(1, 5, "15.00"),
                owes(2, 3, "25.00"), owes(2, 4, "30.00"),
                owes(3, 4, "5.00"), owes(3, 5, "35.00"),
                owes(4, 5, "12.00"));

        SettlementPlan plan = planner.plan(debts);

        assertThat(plan.naiveTransactionCount()).isEqualTo(8);
        assertThat(plan.transactionCount()).isLessThanOrEqualTo(4);   // n - 1 for n = 5
        assertThat(plan.transactionCount()).isLessThan(plan.naiveTransactionCount());
        assertPlanSettlesEverything(debts, plan);
    }

    @Test
    void fullySettledGroupReturnsEmptyPlan() {
        // 1 owed 2 fifty, then paid it back: the pair nets to zero, nothing to do.
        List<Debt> debts = List.of(owes(1, 2, "50.00"), owes(2, 1, "50.00"));

        SettlementPlan plan = planner.plan(debts);

        assertThat(plan.transactions()).isEmpty();
        assertThat(plan.transactionCount()).isZero();
        assertThat(plan.naiveTransactionCount()).isZero();
    }

    @Test
    void groupWithNoDebtsReturnsEmptyPlan() {
        // A single-member group, or one with no expenses yet, has no debts at all.
        SettlementPlan plan = planner.plan(List.of());

        assertThat(plan.transactions()).isEmpty();
        assertThat(plan.naiveTransactionCount()).isZero();
    }

    @Test
    void mutualDebtsAreNettedBeforeCountingNaiveTransactions() {
        // 1 owes 2 thirty, 2 owes 1 ten: naively that is ONE transfer of 20, not two.
        List<Debt> debts = List.of(owes(1, 2, "30.00"), owes(2, 1, "10.00"));

        SettlementPlan plan = planner.plan(debts);

        assertThat(plan.naiveTransactionCount()).isEqualTo(1);
        assertThat(plan.transactions()).containsExactly(new Transfer(1L, 2L, new BigDecimal("20.00")));
    }

    @Test
    void greedyCanLoseToDirectPaymentsOnSparseGraphsSoThePlanTakesTheShorter() {
        // Three unrelated debts: A owes B 7, C owes D 9, E owes B 3. Paying them directly
        // is 3 transfers. Greedy pairs the biggest creditor B (10) with the biggest debtor
        // C (9), who never owed B anything, and then has to mop up the fragments: 4 transfers.
        List<Debt> debts = List.of(owes(1, 2, "7.00"), owes(3, 4, "9.00"), owes(5, 2, "3.00"));

        List<Transfer> greedyAlone = planner.simplify(BalanceService.netBalances(List.of(), debts));
        SettlementPlan plan = planner.plan(debts);

        assertThat(greedyAlone).hasSize(4);
        assertThat(plan.naiveTransactionCount()).isEqualTo(3);
        assertThat(plan.transactionCount()).isEqualTo(3);
        assertThat(plan.transactions()).containsExactly(
                new Transfer(1L, 2L, new BigDecimal("7.00")),
                new Transfer(3L, 4L, new BigDecimal("9.00")),
                new Transfer(5L, 2L, new BigDecimal("3.00"))
        );
        assertPlanSettlesEverything(debts, plan);
    }

    @Test
    void neverExceedsNMinusOneTransactionsAcrossRandomLedgers() {
        Random random = new Random(20260921);   // fixed seed: the sweep is reproducible
        for (int round = 0; round < 500; round++) {
            int people = 2 + random.nextInt(9);   // 2..10 members
            List<Debt> debts = new ArrayList<>();
            int edges = 1 + random.nextInt(people * 3);
            for (int i = 0; i < edges; i++) {
                long from = 1 + random.nextInt(people);
                long to = 1 + random.nextInt(people);
                if (from == to) {
                    continue;
                }
                debts.add(new Debt(from, to, BigDecimal.valueOf(1 + random.nextInt(50_000), 2)));
            }

            SettlementPlan plan = planner.plan(debts);

            long nonZero = nonZeroBalances(debts);
            assertThat(plan.transactionCount()).as("round %d", round).isLessThanOrEqualTo(Math.max(0, (int) nonZero - 1));
            assertThat(plan.transactionCount()).as("round %d", round).isLessThanOrEqualTo(plan.naiveTransactionCount());
            assertPlanSettlesEverything(debts, plan);
        }
    }

    @Test
    void tiesAreBrokenByUserIdSoPlansAreDeterministic() {
        // Two identical creditors and two identical debtors: without a tie-break the
        // heap order would be arbitrary and two runs could produce different plans.
        List<Debt> debts = List.of(owes(4, 1, "10.00"), owes(3, 2, "10.00"));

        SettlementPlan plan = planner.plan(debts);

        assertThat(plan.transactions()).containsExactly(
                new Transfer(3L, 1L, new BigDecimal("10.00")),
                new Transfer(4L, 2L, new BigDecimal("10.00"))
        );
    }
}

package com.uttkarsh.billsync.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * Turns a tangle of who-owes-whom into the fewest sensible payments.
 *
 * <p>Greedy two-heap algorithm: put everyone who is owed money in a max-heap by
 * amount, everyone who owes money in a max-heap by amount, then repeatedly pay
 * the biggest debtor to the biggest creditor for {@code min(debt, credit)}.
 * Every step fully clears at least one person, so a group with {@code n} people
 * holding non-zero balances needs at most {@code n - 1} payments.
 *
 * <p>That is a bound, not the true minimum. The genuine optimum requires finding
 * subsets of people whose balances sum to zero and settling each subset on its
 * own, which is a variant of the partition problem and NP-hard. For the size of
 * group a bill splitter sees, greedy is the right trade: O(n log n), deterministic,
 * and usually far fewer payments than the raw who-owes-whom graph.
 *
 * <p>Usually, not always. When the debt graph is sparse (a few unrelated pairs),
 * greedy can pair the biggest creditor with a debtor from a different pair and end
 * up with <em>more</em> payments than simply paying each debt directly. So the
 * planner computes both and returns whichever is shorter, preferring greedy on a
 * tie. The result is never worse than the direct graph and never more than n - 1.
 */
@Service
public class SettlementPlanner {

    /** One payment in the plan: {@code fromUserId} pays {@code toUserId} {@code amount}. */
    public record Transfer(Long fromUserId, Long toUserId, BigDecimal amount) {
    }

    /**
     * @param transactions          the simplified payments, in the order the greedy pass produced them
     * @param naiveTransactionCount how many payments it would take without simplification: one per
     *                              pair of people who still owe each other something after netting
     *                              what they owe each other in both directions
     */
    public record SettlementPlan(List<Transfer> transactions, int naiveTransactionCount) {

        public int transactionCount() {
            return transactions.size();
        }
    }

    /** A person's outstanding position while the heaps are being drained. */
    private record Position(Long userId, BigDecimal amount) {
    }

    /** Largest amount first; equal amounts fall back to the lower user id so plans are reproducible. */
    private static final Comparator<Position> LARGEST_FIRST =
            Comparator.comparing(Position::amount).reversed().thenComparing(Position::userId);

    public SettlementPlan plan(List<Debt> debts) {
        List<Transfer> direct = directTransfers(debts);
        List<Transfer> greedy = simplify(BalanceService.netBalances(List.of(), debts));
        List<Transfer> chosen = greedy.size() <= direct.size() ? greedy : direct;
        return new SettlementPlan(chosen, direct.size());
    }

    /** The two-heap greedy pass over net balances. */
    List<Transfer> simplify(Map<Long, BigDecimal> netBalances) {
        PriorityQueue<Position> creditors = new PriorityQueue<>(LARGEST_FIRST);
        PriorityQueue<Position> debtors = new PriorityQueue<>(LARGEST_FIRST);
        netBalances.forEach((userId, balance) -> {
            if (balance.signum() > 0) {
                creditors.add(new Position(userId, balance));
            } else if (balance.signum() < 0) {
                debtors.add(new Position(userId, balance.negate()));
            }
        });

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Position creditor = creditors.poll();
            Position debtor = debtors.poll();
            BigDecimal paid = creditor.amount().min(debtor.amount());
            transfers.add(new Transfer(debtor.userId(), creditor.userId(), paid));

            BigDecimal creditLeft = creditor.amount().subtract(paid);
            if (creditLeft.signum() > 0) {
                creditors.add(new Position(creditor.userId(), creditLeft));
            }
            BigDecimal debtLeft = debtor.amount().subtract(paid);
            if (debtLeft.signum() > 0) {
                debtors.add(new Position(debtor.userId(), debtLeft));
            }
        }
        if (!creditors.isEmpty() || !debtors.isEmpty()) {
            // Balances that sum to zero always drain both heaps together. Anything else is a splitter bug.
            throw new IllegalStateException("Balances do not sum to zero: " + netBalances);
        }
        return transfers;
    }

    /**
     * The payments the group would make with no simplification at all: each pair
     * nets what they owe each other in both directions, and every pair left with a
     * non-zero amount is one payment. Ordered by payer then payee so the list is reproducible.
     */
    List<Transfer> directTransfers(List<Debt> debts) {
        Map<Pair, BigDecimal> netByPair = new TreeMap<>();
        for (Debt debt : debts) {
            Pair pair = Pair.of(debt.fromUserId(), debt.toUserId());
            // Amount is positive when the lower id owes the higher id, negative the other way.
            BigDecimal signed = debt.fromUserId().equals(pair.low()) ? debt.amount() : debt.amount().negate();
            netByPair.merge(pair, signed, BigDecimal::add);
        }
        List<Transfer> transfers = new ArrayList<>();
        netByPair.forEach((pair, net) -> {
            if (net.signum() > 0) {
                transfers.add(new Transfer(pair.low(), pair.high(), net));
            } else if (net.signum() < 0) {
                transfers.add(new Transfer(pair.high(), pair.low(), net.negate()));
            }
        });
        transfers.sort(Comparator.comparing(Transfer::fromUserId).thenComparing(Transfer::toUserId));
        return transfers;
    }

    /** An unordered pair of users, normalised so the lower id comes first. */
    private record Pair(Long low, Long high) implements Comparable<Pair> {

        static Pair of(Long a, Long b) {
            return a < b ? new Pair(a, b) : new Pair(b, a);
        }

        @Override
        public int compareTo(Pair other) {
            int byLow = low.compareTo(other.low);
            return byLow != 0 ? byLow : high.compareTo(other.high);
        }
    }
}

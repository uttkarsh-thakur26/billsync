package com.uttkarsh.billsync.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Divides an expense amount among participants so that the shares always sum to
 * the amount exactly.
 *
 * <p>The problem: 100.00 split three ways is 33.333... per head. Rounding every
 * share to two decimals gives 33.33 x 3 = 99.99 and a paisa has vanished. The fix
 * has two parts. First, each base share is rounded <em>down</em>, so the error is
 * always a shortfall and never an overshoot. Second, the shortfall is a whole
 * number of paise (strictly fewer than there are participants), and it is handed
 * out one paisa at a time to participants in ascending user-id order. Ordering by
 * id, not by input order, makes the result deterministic: the same expense always
 * produces the same shares.
 *
 * <p>Pure and stateless: no repositories, no clock, no Spring context needed to test it.
 */
@Service
public class SplitCalculator {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ONE_PAISA = new BigDecimal("0.01");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /** One participant's portion of an expense. Amount always has exactly two decimals. */
    public record Allocation(Long userId, BigDecimal amount) {
    }

    /** Everyone owes the same, give or take the one paisa that rounding left over. */
    public List<Allocation> splitEqually(BigDecimal amount, Collection<Long> participantUserIds) {
        BigDecimal total = requireMoney(amount, "Expense amount");
        List<Long> ids = requireDistinctParticipants(participantUserIds);

        BigDecimal baseShare = total.divide(BigDecimal.valueOf(ids.size()), MONEY_SCALE, RoundingMode.DOWN);
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        for (Long id : ids) {
            shares.put(id, baseShare);
        }
        return distributeRemainder(total, shares);
    }

    /** The client states each share. Nothing is corrected: the amounts must already add up. */
    public List<Allocation> splitExactly(BigDecimal amount, Map<Long, BigDecimal> amountByUserId) {
        BigDecimal total = requireMoney(amount, "Expense amount");
        List<Long> ids = requireDistinctParticipants(amountByUserId == null ? null : amountByUserId.keySet());

        BigDecimal sum = BigDecimal.ZERO;
        List<Allocation> shares = new ArrayList<>(ids.size());
        for (Long id : ids) {
            BigDecimal share = requireMoney(amountByUserId.get(id), "Exact amount for user " + id);
            sum = sum.add(share);
            shares.add(new Allocation(id, share));
        }
        if (sum.compareTo(total) != 0) {
            throw new InvalidSplitException(
                    "Exact split amounts must sum to the expense total: amounts sum to " + sum + " but the expense is " + total);
        }
        return shares;
    }

    /** Each participant owes a percentage of the total. Rounding leftovers are handled like an equal split. */
    public List<Allocation> splitByPercentage(BigDecimal amount, Map<Long, BigDecimal> percentageByUserId) {
        BigDecimal total = requireMoney(amount, "Expense amount");
        List<Long> ids = requireDistinctParticipants(percentageByUserId == null ? null : percentageByUserId.keySet());

        BigDecimal percentageSum = BigDecimal.ZERO;
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        for (Long id : ids) {
            BigDecimal percentage = percentageByUserId.get(id);
            if (percentage == null || percentage.signum() <= 0) {
                throw new InvalidSplitException("Percentage for user " + id + " must be positive");
            }
            percentageSum = percentageSum.add(percentage);
            shares.put(id, total.multiply(percentage).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.DOWN));
        }
        if (percentageSum.compareTo(ONE_HUNDRED) != 0) {
            throw new InvalidSplitException("Percentages must sum to 100 but sum to " + percentageSum);
        }
        return distributeRemainder(total, shares);
    }

    /**
     * Hands out whatever rounding down left over, one paisa per participant in
     * ascending id order, until the shares sum to the total. Because every share
     * was rounded down by less than a paisa, the remainder is fewer paise than
     * there are participants, so a single pass is enough; the loop still cycles
     * rather than assuming that, so it can never hand out less than the total.
     */
    private List<Allocation> distributeRemainder(BigDecimal total, Map<Long, BigDecimal> sharesByUserId) {
        BigDecimal allocated = sharesByUserId.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = total.subtract(allocated);
        if (remainder.signum() < 0) {
            throw new IllegalStateException("Rounding down can never allocate more than the total, but got " + allocated + " for " + total);
        }

        List<Long> ids = new ArrayList<>(sharesByUserId.keySet());
        int next = 0;
        while (remainder.signum() > 0) {
            Long id = ids.get(next % ids.size());
            sharesByUserId.put(id, sharesByUserId.get(id).add(ONE_PAISA));
            remainder = remainder.subtract(ONE_PAISA);
            next++;
        }

        List<Allocation> result = new ArrayList<>(ids.size());
        sharesByUserId.forEach((id, share) -> result.add(new Allocation(id, share)));
        return result;
    }

    /** A valid money amount: present, positive, and representable in whole paise. Returned at scale 2. */
    private static BigDecimal requireMoney(BigDecimal value, String what) {
        if (value == null) {
            throw new InvalidSplitException(what + " is required");
        }
        BigDecimal money;
        try {
            money = value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidSplitException(what + " must have at most two decimal places: " + value);
        }
        if (money.signum() <= 0) {
            throw new InvalidSplitException(what + " must be positive: " + value);
        }
        return money;
    }

    /** At least one participant, each listed once. Returned sorted by id so remainder distribution is deterministic. */
    private static List<Long> requireDistinctParticipants(Collection<Long> participantUserIds) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            throw new InvalidSplitException("At least one participant is required");
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : participantUserIds) {
            if (id == null) {
                throw new InvalidSplitException("Participant user id must not be null");
            }
            if (!seen.add(id)) {
                throw new InvalidSplitException("Each participant may appear only once; user " + id + " is repeated");
            }
        }
        return new ArrayList<>(new TreeSet<>(seen));
    }
}

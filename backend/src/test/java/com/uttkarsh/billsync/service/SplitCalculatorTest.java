package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.service.SplitCalculator.Allocation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The invariant under test everywhere: the shares of an expense must sum to the
 * expense amount EXACTLY. Not approximately, not "within a paisa". Exactly.
 */
class SplitCalculatorTest {

    private final SplitCalculator calculator = new SplitCalculator();

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /** Sum of all allocated amounts, using exact BigDecimal arithmetic. */
    private static BigDecimal sumOf(List<Allocation> shares) {
        return shares.stream().map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void assertSumsExactlyTo(List<Allocation> shares, String expectedTotal) {
        // isEqualTo on BigDecimal also compares scale, so "100.00" != "100.0" != "100".
        // Every share must be a real two-decimal money amount, and so must their sum.
        assertThat(sumOf(shares)).isEqualTo(money(expectedTotal));
        assertThat(shares).allSatisfy(share -> assertThat(share.amount().scale()).isEqualTo(2));
    }

    @Nested
    class EqualSplit {

        @Test
        void hundredSplitThreeWaysSumsToExactlyHundred() {
            // 100.00 / 3 = 33.333... Rounded, that is 33.33 each, and 3 x 33.33 = 99.99.
            // One paisa has vanished. The calculator must find it and give it to someone.
            List<Allocation> shares = calculator.splitEqually(money("100.00"), List.of(1L, 2L, 3L));

            assertSumsExactlyTo(shares, "100.00");
            assertThat(shares).containsExactly(
                    new Allocation(1L, money("33.34")),   // lowest user id absorbs the leftover paisa
                    new Allocation(2L, money("33.33")),
                    new Allocation(3L, money("33.33"))
            );
        }

        @Test
        void onePaisaSplitThreeWays() {
            // Base share rounds DOWN to 0.00 for everyone; the whole amount is remainder.
            List<Allocation> shares = calculator.splitEqually(money("0.01"), List.of(1L, 2L, 3L));

            assertSumsExactlyTo(shares, "0.01");
            assertThat(shares).containsExactly(
                    new Allocation(1L, money("0.01")),
                    new Allocation(2L, money("0.00")),
                    new Allocation(3L, money("0.00"))
            );
        }

        @Test
        void thirtyThreePointThreeThreeSplitSevenWays() {
            // 33.33 / 7 = 4.7614... -> base 4.76, and 7 x 4.76 = 33.32, so one paisa is left over.
            List<Allocation> shares = calculator.splitEqually(
                    money("33.33"), List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));

            assertSumsExactlyTo(shares, "33.33");
            assertThat(shares).extracting(Allocation::amount).containsExactly(
                    money("4.77"), money("4.76"), money("4.76"), money("4.76"),
                    money("4.76"), money("4.76"), money("4.76"));
        }

        @Test
        void remainderGoesToLowestUserIdsRegardlessOfInputOrder() {
            // Same expense submitted with participants in a different order must
            // produce the same shares, otherwise a retry could change who owes what.
            List<Allocation> shares = calculator.splitEqually(money("100.00"), List.of(3L, 1L, 2L));

            assertSumsExactlyTo(shares, "100.00");
            assertThat(shares).containsExactly(
                    new Allocation(1L, money("33.34")),
                    new Allocation(2L, money("33.33")),
                    new Allocation(3L, money("33.33"))
            );
        }

        @Test
        void singleParticipantOwesEverything() {
            List<Allocation> shares = calculator.splitEqually(money("57.10"), List.of(9L));

            assertSumsExactlyTo(shares, "57.10");
            assertThat(shares).containsExactly(new Allocation(9L, money("57.10")));
        }

        @Test
        void sharesNeverDifferByMoreThanOnePaisaAcrossManyAmounts() {
            // Brute-force the invariant: every amount from 0.01 to 20.00 split 1..12 ways.
            for (int paise = 1; paise <= 2000; paise++) {
                BigDecimal amount = BigDecimal.valueOf(paise, 2);
                for (int n = 1; n <= 12; n++) {
                    List<Long> ids = java.util.stream.LongStream.rangeClosed(1, n).boxed().toList();
                    List<Allocation> shares = calculator.splitEqually(amount, ids);

                    assertThat(sumOf(shares)).as("%s split %d ways", amount, n).isEqualByComparingTo(amount);
                    BigDecimal max = shares.stream().map(Allocation::amount).max(BigDecimal::compareTo).orElseThrow();
                    BigDecimal min = shares.stream().map(Allocation::amount).min(BigDecimal::compareTo).orElseThrow();
                    assertThat(max.subtract(min)).as("%s split %d ways", amount, n).isLessThanOrEqualTo(money("0.01"));
                }
            }
        }

        @Test
        void rejectsEmptyParticipants() {
            assertThatThrownBy(() -> calculator.splitEqually(money("10.00"), List.of()))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("participant");
        }

        @Test
        void rejectsDuplicateParticipants() {
            assertThatThrownBy(() -> calculator.splitEqually(money("10.00"), List.of(1L, 2L, 1L)))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("once");
        }

        @Test
        void rejectsNonPositiveAmount() {
            assertThatThrownBy(() -> calculator.splitEqually(money("0.00"), List.of(1L, 2L)))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        void rejectsAmountWithMoreThanTwoDecimals() {
            assertThatThrownBy(() -> calculator.splitEqually(money("10.001"), List.of(1L, 2L)))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("two decimal");
        }
    }

    @Nested
    class ExactSplit {

        @Test
        void amountsThatSumToTheTotalAreKeptAsGiven() {
            List<Allocation> shares = calculator.splitExactly(
                    money("100.00"), Map.of(2L, money("30.00"), 1L, money("70.00")));

            assertSumsExactlyTo(shares, "100.00");
            assertThat(shares).containsExactly(
                    new Allocation(1L, money("70.00")),
                    new Allocation(2L, money("30.00"))
            );
        }

        @Test
        void amountsThatDoNotSumToTheTotalAreRejected() {
            // 50.00 + 49.99 = 99.99, one paisa short. No silent correction: the client's numbers are wrong.
            assertThatThrownBy(() -> calculator.splitExactly(
                    money("100.00"), Map.of(1L, money("50.00"), 2L, money("49.99"))))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("sum to the expense total");
        }

        @Test
        void amountsThatOvershootTheTotalAreRejected() {
            assertThatThrownBy(() -> calculator.splitExactly(
                    money("100.00"), Map.of(1L, money("50.00"), 2L, money("50.01"))))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("sum to the expense total");
        }

        @Test
        void integerAndTwoDecimalAmountsCompareAsMoney() {
            // "70" and "70.00" are the same money. The sum check must not be fooled by scale.
            List<Allocation> shares = calculator.splitExactly(
                    money("100.00"), Map.of(1L, money("70"), 2L, money("30.0")));

            assertSumsExactlyTo(shares, "100.00");
        }

        @Test
        void rejectsNonPositiveShare() {
            assertThatThrownBy(() -> calculator.splitExactly(
                    money("100.00"), Map.of(1L, money("100.00"), 2L, money("0.00"))))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("positive");
        }
    }

    @Nested
    class PercentageSplit {

        @Test
        void percentagesThatSumToHundredAreApplied() {
            List<Allocation> shares = calculator.splitByPercentage(
                    money("200.00"), Map.of(1L, money("50"), 2L, money("25"), 3L, money("25")));

            assertSumsExactlyTo(shares, "200.00");
            assertThat(shares).containsExactly(
                    new Allocation(1L, money("100.00")),
                    new Allocation(2L, money("50.00")),
                    new Allocation(3L, money("50.00"))
            );
        }

        @Test
        void remainderFromRoundingDownIsDistributedByUserId() {
            // 0.10 at 33.33% is 0.0333 -> 0.03 each, and 33.34% is 0.0334 -> 0.03.
            // That sums to 0.09; the missing paisa goes to the lowest user id.
            List<Allocation> shares = calculator.splitByPercentage(
                    money("0.10"), Map.of(1L, money("33.33"), 2L, money("33.33"), 3L, money("33.34")));

            assertSumsExactlyTo(shares, "0.10");
            assertThat(shares).containsExactly(
                    new Allocation(1L, money("0.04")),
                    new Allocation(2L, money("0.03")),
                    new Allocation(3L, money("0.03"))
            );
        }

        @Test
        void percentagesThatDoNotSumToHundredAreRejected() {
            assertThatThrownBy(() -> calculator.splitByPercentage(
                    money("100.00"), Map.of(1L, money("50"), 2L, money("49"))))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("sum to 100");
        }

        @Test
        void percentagesOverHundredAreRejected() {
            assertThatThrownBy(() -> calculator.splitByPercentage(
                    money("100.00"), Map.of(1L, money("60"), 2L, money("50"))))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("sum to 100");
        }

        @Test
        void rejectsNonPositivePercentage() {
            assertThatThrownBy(() -> calculator.splitByPercentage(
                    money("100.00"), Map.of(1L, money("100"), 2L, money("0"))))
                    .isInstanceOf(InvalidSplitException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        void invariantHoldsForAwkwardPercentagesAcrossManyAmounts() {
            Map<Long, BigDecimal> thirds = Map.of(1L, money("33.33"), 2L, money("33.33"), 3L, money("33.34"));
            Map<Long, BigDecimal> sevenths = Map.of(
                    1L, money("14.29"), 2L, money("14.29"), 3L, money("14.29"), 4L, money("14.29"),
                    5L, money("14.28"), 6L, money("14.28"), 7L, money("14.28"));

            for (int paise = 1; paise <= 2000; paise++) {
                BigDecimal amount = BigDecimal.valueOf(paise, 2);
                assertThat(sumOf(calculator.splitByPercentage(amount, thirds))).as("thirds of %s", amount)
                        .isEqualByComparingTo(amount);
                assertThat(sumOf(calculator.splitByPercentage(amount, sevenths))).as("sevenths of %s", amount)
                        .isEqualByComparingTo(amount);
            }
        }
    }
}

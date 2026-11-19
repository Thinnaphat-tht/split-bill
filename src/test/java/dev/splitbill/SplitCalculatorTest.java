package dev.splitbill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SplitCalculatorTest {

    @Test
    void sharedDishAndExtrasAddUpExactly() {
        var req = new SplitCalculator.Request(
                List.of("A", "B", "C"),
                List.of(new SplitCalculator.Item("ข้าวผัด", 6000, List.of(0)),
                        new SplitCalculator.Item("ต้มยำ", 25000, List.of(0, 1, 2)),
                        new SplitCalculator.Item("ชาเย็น", 4500, List.of(1))),
                3557); // 10% service + 7% VAT, rounded
        var result = SplitCalculator.split(req);

        long sum = result.stream().mapToLong(SplitCalculator.PersonTotal::total).sum();
        assertEquals(6000 + 25000 + 4500 + 3557, sum);
        assertEquals(6000 + 8334, result.get(0).subtotal()); // 25000/3 remainder goes to someone
        assertEquals(2, result.get(0).shares().size());
    }

    @Test
    void discountIsNegativeExtra() {
        var req = new SplitCalculator.Request(List.of("A", "B"),
                List.of(new SplitCalculator.Item("x", 10000, List.of(0)),
                        new SplitCalculator.Item("y", 30000, List.of(1))),
                -4000);
        var r = SplitCalculator.split(req);
        assertEquals(9000, r.get(0).total());
        assertEquals(27000, r.get(1).total());
    }

    @Test
    void itemWithoutEaterIsRejected() {
        var req = new SplitCalculator.Request(List.of("A"),
                List.of(new SplitCalculator.Item("x", 100, List.of())), 0);
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.split(req));
    }

    @Test
    void divideUsesLargestRemainder() {
        assertArrayEquals(new long[]{34, 33, 33}, SplitCalculator.divide(100, new long[]{1, 1, 1}));
        assertArrayEquals(new long[]{-34, -33, -33}, SplitCalculator.divide(-100, new long[]{1, 1, 1}));
    }

    @Test
    void realisticBillMatchesHandCalculation() {
        // A: rice 120, B: tea 90, everyone shares hotpot 300 -> subtotal 510
        // service 10% = 51, VAT 7% of 561 = 39.27, extra 90.27, bill 600.27
        // A ate 220 -> 220/510 of 90.27 = 38.94, B 190 -> 33.63, C 100 -> 17.70
        var r = SplitCalculator.split(new SplitCalculator.Request(List.of("A", "B", "C"),
                List.of(new SplitCalculator.Item("rice", 12000, List.of(0)),
                        new SplitCalculator.Item("tea", 9000, List.of(1)),
                        new SplitCalculator.Item("hotpot", 30000, List.of(0, 1, 2))),
                9027));
        assertEquals(25894, r.get(0).total());
        assertEquals(22363, r.get(1).total());
        assertEquals(11770, r.get(2).total());
    }

    @Test
    void personWhoAteNothingPaysNothing() {
        var r = SplitCalculator.split(new SplitCalculator.Request(List.of("A", "B"),
                List.of(new SplitCalculator.Item("x", 41500, List.of(0))), 3000));
        assertEquals(44500, r.get(0).total());
        assertEquals(0, r.get(1).total());
    }

    @Test
    void randomBillsAlwaysAddUpAndStayFair() {
        Random rnd = new Random(42);
        for (int run = 0; run < 5000; run++) {
            int n = 1 + rnd.nextInt(8);
            List<String> people = new ArrayList<>();
            for (int i = 0; i < n; i++) people.add("P" + i);
            List<SplitCalculator.Item> items = new ArrayList<>();
            int count = 1 + rnd.nextInt(15);
            long itemSum = 0;
            for (int k = 0; k < count; k++) {
                long price = 1 + rnd.nextInt(200000);
                List<Integer> eaters = new ArrayList<>();
                for (int i = 0; i < n; i++) if (rnd.nextBoolean()) eaters.add(i);
                if (eaters.isEmpty()) eaters.add(rnd.nextInt(n));
                items.add(new SplitCalculator.Item("i" + k, price, eaters));
                itemSum += price;
            }
            long extra = Math.round(itemSum * (rnd.nextDouble() * 0.4 - 0.2)); // -20%..+20%
            var r = SplitCalculator.split(new SplitCalculator.Request(people, items, extra));

            assertEquals(itemSum + extra, r.stream().mapToLong(SplitCalculator.PersonTotal::total).sum());
            for (int i = 0; i < n; i++) {
                // exact share with fractions, before any rounding to satang
                double eaten = 0;
                int dishes = 0;
                for (var it : items) if (it.eaters().contains(i)) { eaten += (double) it.price() / it.eaters().size(); dishes++; }
                double ideal = eaten + extra * eaten / itemSum;
                // each dish can be off by under 1 satang, plus 1 for the extra and 1 for float slack
                assertTrue(Math.abs(r.get(i).total() - ideal) <= dishes + 2,
                        "run " + run + " person " + i + ": got " + r.get(i).total() + " ideal " + ideal);
            }
        }
    }
}

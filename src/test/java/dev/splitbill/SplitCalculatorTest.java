package dev.splitbill;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}

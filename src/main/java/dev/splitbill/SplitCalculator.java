package dev.splitbill;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a bill between people. All money is in satang (1 baht = 100 satang)
 * so totals always add up exactly.
 */
public final class SplitCalculator {

    /** One line on the receipt and the indexes of the people who shared it. */
    public record Item(String name, long price, List<Integer> eaters) {}

    /** extra = service charge + VAT - discount, anything not in item prices. */
    public record Request(List<String> people, List<Item> items, long extra) {}

    public record Share(String item, long amount) {}

    public record PersonTotal(String name, List<Share> shares, long subtotal, long extra, long total) {}

    private SplitCalculator() {}

    public static List<PersonTotal> split(Request req) {
        int n = req.people().size();
        if (n == 0) throw new IllegalArgumentException("ต้องมีอย่างน้อย 1 คน");

        List<List<Share>> shares = new ArrayList<>();
        long[] subtotal = new long[n];
        for (int i = 0; i < n; i++) shares.add(new ArrayList<>());

        for (Item item : req.items()) {
            List<Integer> eaters = item.eaters();
            if (eaters == null || eaters.isEmpty()) {
                throw new IllegalArgumentException("รายการ \"" + item.name() + "\" ยังไม่มีคนกิน");
            }
            long[] parts = divide(item.price(), weightsEqual(eaters.size()));
            for (int k = 0; k < eaters.size(); k++) {
                int p = eaters.get(k);
                if (p < 0 || p >= n) throw new IllegalArgumentException("ไม่มีคนลำดับที่ " + p);
                subtotal[p] += parts[k];
                shares.get(p).add(new Share(item.name(), parts[k]));
            }
        }

        // service charge / VAT / discount are shared in proportion to what each person ate
        long[] extra = divide(req.extra(), subtotal);

        List<PersonTotal> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(new PersonTotal(req.people().get(i), shares.get(i),
                    subtotal[i], extra[i], subtotal[i] + extra[i]));
        }
        return result;
    }

    private static long[] weightsEqual(int count) {
        long[] w = new long[count];
        java.util.Arrays.fill(w, 1);
        return w;
    }

    /**
     * Divides amount by weights using the largest-remainder method,
     * so the parts always sum to exactly amount. Works for negative amounts.
     */
    static long[] divide(long amount, long[] weights) {
        long[] parts = new long[weights.length];
        long totalWeight = 0;
        for (long w : weights) totalWeight += w;
        if (totalWeight == 0) {
            // nobody ate anything priced: split evenly instead
            if (weights.length == 0) return parts;
            return divide(amount, weightsEqual(weights.length));
        }

        long sign = amount < 0 ? -1 : 1;
        long abs = Math.abs(amount);
        long given = 0;
        long[] rem = new long[weights.length];
        for (int i = 0; i < weights.length; i++) {
            long num = abs * weights[i];
            parts[i] = num / totalWeight;
            rem[i] = num % totalWeight;
            given += parts[i];
        }
        for (long left = abs - given; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < rem.length; i++) if (rem[i] > rem[best]) best = i;
            parts[best]++;
            rem[best] = -1;
        }
        for (int i = 0; i < parts.length; i++) parts[i] *= sign;
        return parts;
    }
}

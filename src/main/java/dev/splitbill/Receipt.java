package dev.splitbill;

import java.util.List;

/** A receipt read from a photo. Money is in baht. */
public record Receipt(List<Line> items, double serviceCharge, double vat, double discount,
                      double total, String source, boolean balanced) {

    public record Line(String name, int qty, double price) {}

    /** True when items + service + VAT - discount matches the printed total (within 1 baht). */
    public static boolean isBalanced(List<Line> items, double service, double vat, double discount, double total) {
        if (items.isEmpty() || total <= 0) return false;
        double sum = items.stream().mapToDouble(Line::price).sum() + service + vat - discount;
        return Math.abs(sum - total) <= 1.0;
    }

    public Receipt withSource(String newSource) {
        return new Receipt(items, serviceCharge, vat, discount, total, newSource, balanced);
    }
}

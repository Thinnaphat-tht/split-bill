package dev.splitbill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw OCR text of a receipt into items and totals.
 * Works line by line: a line that ends with a price is either a known
 * summary line (total, VAT, service, discount) or a menu item.
 */
public final class ReceiptParser {

    // price at the end of a line: 1,250.00 / 250.00 / 250.0 (OCR sometimes drops a digit)
    // must follow a space, so times (12:45), dates and IDs (TID#2611...) are not prices;
    // whole numbers without decimals are capped at 5 digits to skip receipt numbers
    private static final Pattern PRICE_AT_END = Pattern.compile("(?<=\\s)(-?\\d{1,3}(?:[,.]\\d{3})*[.,]\\d{1,2}|-?\\d{1,5})\\s*(?:฿|บาท|THB)?\\s*$");
    // quantity at the start: "2 ชาเย็น", "2x ชาเย็น", "2 X Thai Tea"
    private static final Pattern QTY_AT_START = Pattern.compile("^(\\d{1,2})\\s*[xX×]?\\s+(.+)$");
    // quantity after the name: "ชาเย็น x2"
    // OCR may read the "x" as % or *
    private static final Pattern QTY_AFTER = Pattern.compile("^(.+?)\\s+[xX×@%*]\\s*(\\d{1,2})\\b.*$");

    private static final String[] TOTAL = {"grand total", "net total", "total", "รวมทั้งสิ้น", "ยอดสุทธิ", "สุทธิ", "ยอดรวมสุทธิ", "amount due"};
    private static final String[] SUBTOTAL = {"subtotal", "sub total", "sub-total", "รวมเงิน", "ยอดรวม"};
    private static final String[] SERVICE = {"service", "ค่าบริการ", "s/c", "sc "};
    private static final String[] VAT = {"vat", "ภาษี", "tax"};
    private static final String[] DISCOUNT = {"discount", "ส่วนลด", "ลด "};
    private static final String[] IGNORE = {"cash", "change", "เงินสด", "เงินทอน", "ทอน", "รับเงิน", "card", "visa",
            "tel", "โทร", "tax id", "เลขประจำตัว", "table", "โต๊ะ", "date", "วันที่", "qr", "promptpay", "พร้อมเพย์",
            // payment method lines repeat the total, they are not items
            "wallet", "วอล", "call", "ทรูมันนี่", "truemoney", "rabbit", "line pay", "credit", "เครดิต", "ชำระ", "paid",
            "เลขที่", "ใบเสร็จ", "รหัส", "สาขา", "สมาชิก", "member", "point", "แต้ม"};

    private ReceiptParser() {}

    public static Receipt parse(String text) {
        List<Receipt.Line> items = new ArrayList<>();
        double service = 0, vat = 0, discount = 0, total = 0;
        boolean vatIncluded = false;

        for (String raw : text.split("\\R")) {
            // OCR writes sara am as nikhahit + sara aa; join them back
            String line = raw.strip().replace("ํา", "ำ").replaceAll("\\s{2,}", " ");
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("include") || lower.contains("รวมภาษี") || lower.contains("รวม vat")) vatIncluded = true;

            Matcher pm = PRICE_AT_END.matcher(line);
            if (!pm.find()) continue;
            double price = toNumber(pm.group(1));
            String label = line.substring(0, pm.start()).strip();
            if (label.isEmpty()) continue;
            String l = label.toLowerCase(Locale.ROOT) + " ";

            // order matters: "total" must win over "subtotal" only when it is not a subtotal
            if (has(l, IGNORE)) continue;
            if (has(l, DISCOUNT)) { discount += Math.abs(price); continue; }
            if (has(l, SERVICE)) { service += price; continue; }
            if (has(l, VAT)) { if (!vatIncluded) vat += price; continue; }
            if (has(l, new String[]{"subtotal", "sub total", "sub-total"})) continue;
            if (has(l, TOTAL)) { total = price; continue; }
            // a bare "รวม" only counts at the start, so dishes like "ข้าวผัดรวมมิตร" stay items
            if (has(l, SUBTOTAL) || l.startsWith("รวม")) continue;
            if (price <= 0) continue;

            items.add(toItem(label, price));
        }

        // when only a subtotal exists and no grand total line was found
        boolean balanced = Receipt.isBalanced(items, service, vat, discount, total);
        return new Receipt(items, round(service), round(vat), round(discount), round(total), "ocr", balanced);
    }

    private static Receipt.Line toItem(String label, double price) {
        int qty = 1;
        String name = label;
        Matcher q = QTY_AT_START.matcher(label);
        if (q.matches()) {
            qty = Integer.parseInt(q.group(1));
            name = q.group(2);
        } else {
            Matcher a = QTY_AFTER.matcher(label);
            if (a.matches()) {
                name = a.group(1);
                qty = Integer.parseInt(a.group(2));
            }
        }
        // drop stray unit-price columns, e.g. "ชาเย็น 45.00" before the line total
        name = name.replaceAll("\\s+\\d+[.,]\\d{2}$", "").replaceAll("[|_*]+", "").strip();
        return new Receipt.Line(name, Math.max(qty, 1), round(price));
    }

    /**
     * Tesseract often drops or swaps Thai tone marks and upper/lower vowels
     * ("รวมทั้งสิ้น" comes back as "รวมทังสืน"), so keywords are compared without them.
     */
    private static boolean has(String text, String[] words) {
        String t = bare(text);
        for (String w : words) if (t.contains(bare(w))) return true;
        return false;
    }

    private static String bare(String s) {
        return s.replaceAll("[\\u0E31\\u0E34-\\u0E3A\\u0E47-\\u0E4E]", "");
    }

    /** "1,250.00" and "1.250,00" style numbers both become 1250.0 */
    static double toNumber(String s) {
        String t = s.replace(" ", "");
        int lastSep = Math.max(t.lastIndexOf('.'), t.lastIndexOf(','));
        if (lastSep >= 0 && t.length() - lastSep - 1 <= 2) {
            String whole = t.substring(0, lastSep).replaceAll("[.,]", "");
            return Double.parseDouble(whole + "." + t.substring(lastSep + 1));
        }
        return Double.parseDouble(t.replaceAll("[.,]", ""));
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

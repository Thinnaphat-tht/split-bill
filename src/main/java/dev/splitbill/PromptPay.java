package dev.splitbill;

import java.nio.charset.StandardCharsets;

/** Builds a Thai PromptPay QR payload (EMVCo format) for a phone number or national ID. */
public final class PromptPay {

    private PromptPay() {}

    /**
     * @param target phone number (10 digits) or national ID / tax ID (13 digits)
     * @param satang amount in satang, or 0 for no fixed amount
     */
    public static String payload(String target, long satang) {
        String id = target.replaceAll("\\D", "");
        String account;
        if (id.length() == 10 && id.startsWith("0")) {
            account = tag("01", "0066" + id.substring(1));
        } else if (id.length() == 13) {
            account = tag("02", id);
        } else {
            throw new IllegalArgumentException("ต้องเป็นเบอร์โทร 10 หลัก หรือเลขบัตรประชาชน 13 หลัก");
        }

        StringBuilder sb = new StringBuilder()
                .append(tag("00", "01"))
                .append(tag("01", satang > 0 ? "12" : "11"))
                .append(tag("29", tag("00", "A000000677010111") + account))
                .append(tag("53", "764"))
                .append(satang > 0 ? tag("54", String.format("%d.%02d", satang / 100, satang % 100)) : "")
                .append(tag("58", "TH"))
                .append("6304");
        return sb + String.format("%04X", crc16(sb.toString()));
    }

    private static String tag(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    /** CRC-16/CCITT-FALSE, as required by the EMVCo QR spec. */
    static int crc16(String s) {
        int crc = 0xFFFF;
        for (byte b : s.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
            }
            crc &= 0xFFFF;
        }
        return crc;
    }
}

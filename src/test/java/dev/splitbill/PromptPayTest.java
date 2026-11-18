package dev.splitbill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptPayTest {

    @Test
    void crcMatchesStandardCheckValue() {
        assertEquals(0x29B1, PromptPay.crc16("123456789"));
    }

    @Test
    void phonePayloadWithAmount() {
        String p = PromptPay.payload("081-234-5678", 12550);
        assertTrue(p.startsWith("000201010212"));
        assertTrue(p.contains("01130066812345678"));
        assertTrue(p.contains("5406125.50"));
        String body = p.substring(0, p.length() - 4);
        assertEquals(String.format("%04X", PromptPay.crc16(body)), p.substring(p.length() - 4));
    }

    @Test
    void rejectsBadId() {
        assertThrows(IllegalArgumentException.class, () -> PromptPay.payload("12345", 100));
    }
}

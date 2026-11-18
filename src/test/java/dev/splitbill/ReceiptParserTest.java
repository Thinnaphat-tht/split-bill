package dev.splitbill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptParserTest {

    @Test
    void englishReceiptWithServiceAndVat() {
        Receipt r = ReceiptParser.parse("""
                SOMTAM HOUSE
                Tel 02-123-4567
                1 Fried Rice      60.00
                1 Tom Yum        250.00
                2 Thai Tea  45.00  90.00
                Subtotal         400.00
                Service 10%       40.00
                VAT 7%            30.80
                TOTAL            470.80
                Cash             500.00
                Change            29.20
                """);
        assertEquals(3, r.items().size());
        assertEquals("Thai Tea", r.items().get(2).name());
        assertEquals(2, r.items().get(2).qty());
        assertEquals(90.0, r.items().get(2).price());
        assertEquals(40.0, r.serviceCharge());
        assertEquals(30.8, r.vat());
        assertEquals(470.8, r.total());
        assertTrue(r.balanced());
    }

    @Test
    void thaiReceiptWithDiscountAndVatIncluded() {
        Receipt r = ReceiptParser.parse("""
                ร้านข้าวมันไก่
                โต๊ะ 5
                ข้าวมันไก่ x2      100.00
                ต้มยำกุ้ง         1,200.00
                ข้าวผัดรวมมิตร       50.00
                รวม               1,350.00
                ส่วนลด             -50.00
                ภาษีมูลค่าเพิ่ม 7% (รวมภาษีแล้ว)  85.98
                รวมทั้งสิ้น        1,300.00
                เงินสด            1,500.00
                """);
        assertEquals(3, r.items().size());
        assertEquals("ข้าวมันไก่", r.items().get(0).name());
        assertEquals(2, r.items().get(0).qty());
        assertEquals(1200.0, r.items().get(1).price());
        assertEquals("ข้าวผัดรวมมิตร", r.items().get(2).name());
        assertEquals(50.0, r.discount());
        assertEquals(0.0, r.vat());
        assertTrue(r.balanced());
    }

    @Test
    void toleratesRealTesseractThaiMistakes() {
        // copied from Tesseract output of a Thai receipt
        Receipt r = ReceiptParser.parse("""
                ร้านข้าวมันไก่

                ข้าวมันไก่ %2        100.00
                ต้มยํากุ้ง                 250.00
                ชาเย็น                       45.00
                ค่าบริการ 10%        39.50
                รวมทังสืน               434.50
                เงินสด                     500.00
                """);
        assertEquals(2, r.items().get(0).qty());
        assertEquals("ข้าวมันไก่", r.items().get(0).name());
        assertEquals("ต้มยำกุ้ง", r.items().get(1).name());
        assertEquals(434.5, r.total());
        assertTrue(r.balanced());
    }

    @Test
    void convenienceStoreReceiptSkipsPaymentAndIdLines() {
        // real Tesseract output of a 7-Eleven receipt
        Receipt r = ReceiptParser.parse("""
                17:51                                           al FT ED
                4         รายการสังซื้อที่ร้านและ 7Delivery
                ]]1/1]/69 | 12:45
                เลขที่ใบเสร็จ                                                      1000104178
                [ซี          สาขา 7-Eleven ชซอยตังสิน (ศาลายา)
                รหัสร้าน : 19486
                รายการสินค้า
                1 ถ้วเหลืองปกโแลคตา              20.00
                1 แชนวิชคริวชองค์หมูห              32.00
                109 โจฉบริบร้าน           @0.00      9.009ม
                ยอดสูทธิ    2 ชั้น         52.00
                ทรวอลเล็ท7๒0           52.00
                TID#261111422132139
                R#1000104178P1 :7875715    11/11/69 12:45
                * ศูนย์บริการสมาชิก All Member 0-2826-7777 =
                """);
        assertEquals(2, r.items().size());
        assertTrue(r.balanced());

        // Tesseract on Linux reads the wallet line differently
        r = ReceiptParser.parse("""
                1 ถั่วเหลืองปกโแลคตา              20.00
                1 แชนวิชคริวชองค์หมูห              32.00
                ยอดสุทธิ    2 ชิ้น         52.00
                ทรวอลแล็ท7๒0           52.00
                """);
        assertEquals(2, r.items().size());
        assertEquals(20.0, r.items().get(0).price());
        assertEquals(32.0, r.items().get(1).price());
        assertEquals(52.0, r.total());
        assertTrue(r.balanced());
    }

    @Test
    void notBalancedWhenOcrMissesALine() {
        Receipt r = ReceiptParser.parse("""
                Tom Yum 250.00
                TOTAL 470.80
                """);
        assertFalse(r.balanced());
    }

    @Test
    void dropsOcrNoiseBeforeItemNames() {
        Receipt r = ReceiptParser.parse("%แชนวิชคริวชองค์หมูห              32.00\nTOTAL 32.00");
        assertEquals("แชนวิชคริวชองค์หมูห", r.items().get(0).name());
    }

    @Test
    void numberFormats() {
        assertEquals(1250.0, ReceiptParser.toNumber("1,250.00"));
        assertEquals(1250.5, ReceiptParser.toNumber("1.250,50"));
        assertEquals(60.0, ReceiptParser.toNumber("60"));
    }
}

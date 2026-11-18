// Same cases as ReceiptParserTest.java. Run: node --test src/test/js
import test from 'node:test';
import assert from 'node:assert/strict';
import { parseReceipt, toNumber } from '../../main/resources/static/js/receipt-parser.js';

test('english receipt with service and VAT', () => {
  const r = parseReceipt(`SOMTAM HOUSE
Tel 02-123-4567
1 Fried Rice      60.00
1 Tom Yum        250.00
2 Thai Tea  45.00  90.00
Subtotal         400.00
Service 10%       40.00
VAT 7%            30.80
TOTAL            470.80
Cash             500.00
Change            29.20`);
  assert.equal(r.items.length, 3);
  assert.deepEqual(r.items[2], { name: 'Thai Tea', qty: 2, price: 90 });
  assert.equal(r.serviceCharge, 40);
  assert.equal(r.vat, 30.8);
  assert.equal(r.total, 470.8);
  assert.ok(r.balanced);
});

test('thai receipt with discount and VAT included', () => {
  const r = parseReceipt(`ร้านข้าวมันไก่
โต๊ะ 5
ข้าวมันไก่ x2      100.00
ต้มยำกุ้ง         1,200.00
ข้าวผัดรวมมิตร       50.00
รวม               1,350.00
ส่วนลด             -50.00
ภาษีมูลค่าเพิ่ม 7% (รวมภาษีแล้ว)  85.98
รวมทั้งสิ้น        1,300.00
เงินสด            1,500.00`);
  assert.equal(r.items.length, 3);
  assert.equal(r.items[0].qty, 2);
  assert.equal(r.items[2].name, 'ข้าวผัดรวมมิตร');
  assert.equal(r.discount, 50);
  assert.equal(r.vat, 0);
  assert.ok(r.balanced);
});

test('tolerates Tesseract mistakes on Thai', () => {
  const r = parseReceipt(`ข้าวมันไก่ %2        100.00
ต้มยํากุ้ง                 250.00
ชาเย็น                       45.00
ค่าบริการ 10%        39.50
รวมทังสืน               434.50
เงินสด                     500.00`);
  assert.equal(r.items[0].qty, 2);
  assert.equal(r.items[1].name, 'ต้มยำกุ้ง');
  assert.equal(r.total, 434.5);
  assert.ok(r.balanced);
});

test('7-Eleven slip skips payment, id and time lines', () => {
  const r = parseReceipt(`17:51 al ? 25
]]1/1]/69 | 12:45
เลขที่ใบเสร็จ                                                      1000104178
รหัสร้าน : 19486
1 ถ้วเหลืองปกโแลคตา              20.00
1 แชนวิชคริวชองค์หมูห              32.00
109 โจฉบริบร้าน           @0.00      9.009ม
ยอดสูทธิ    2 ชั้น         52.00
ทรวอลแล็ท7๒0           52.00
TID#261111422132139`);
  assert.equal(r.items.length, 2);
  assert.equal(r.total, 52);
  assert.ok(r.balanced);
});

test('drops OCR noise before item names', () => {
  const r = parseReceipt('%แชนวิชคริวชองค์หมูห              32.00\nTOTAL 32.00');
  assert.equal(r.items[0].name, 'แชนวิชคริวชองค์หมูห');
});

test('number formats', () => {
  assert.equal(toNumber('1,250.00'), 1250);
  assert.equal(toNumber('1.250,50'), 1250.5);
  assert.equal(toNumber('60'), 60);
});

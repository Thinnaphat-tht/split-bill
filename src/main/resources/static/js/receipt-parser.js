// Turns raw OCR text of a receipt into items and totals.
// Browser port of ReceiptParser.java; keep the two in sync.

// must follow a space, so times (12:45), dates and IDs are not prices;
// whole numbers without decimals are capped at 5 digits to skip receipt numbers
const PRICE_AT_END = /(?<=\s)(-?\d{1,3}(?:[,.]\d{3})*[.,]\d{1,2}|-?\d{1,5})\s*(?:฿|บาท|THB)?\s*$/;
const QTY_AT_START = /^(\d{1,2})\s*[xX×]?\s+(.+)$/;
// OCR may read the "x" as % or *
const QTY_AFTER = /^(.+?)\s+[xX×@%*]\s*(\d{1,2})\b.*$/;

const TOTAL = ['grand total', 'net total', 'total', 'รวมทั้งสิ้น', 'ยอดสุทธิ', 'สุทธิ', 'ยอดรวมสุทธิ', 'amount due'];
const SUBTOTAL = ['subtotal', 'sub total', 'sub-total', 'รวมเงิน', 'ยอดรวม'];
const SERVICE = ['service', 'ค่าบริการ', 's/c', 'sc '];
const VAT = ['vat', 'ภาษี', 'tax'];
const DISCOUNT = ['discount', 'ส่วนลด', 'ลด '];
const IGNORE = ['cash', 'change', 'เงินสด', 'เงินทอน', 'ทอน', 'รับเงิน', 'card', 'visa',
  'tel', 'โทร', 'tax id', 'เลขประจำตัว', 'table', 'โต๊ะ', 'date', 'วันที่', 'qr', 'promptpay', 'พร้อมเพย์',
  // payment method lines repeat the total, they are not items
  'wallet', 'วอล', 'call', 'ทรูมันนี่', 'truemoney', 'rabbit', 'line pay', 'credit', 'เครดิต', 'ชำระ', 'paid',
  'เลขที่', 'ใบเสร็จ', 'รหัส', 'สาขา', 'สมาชิก', 'member', 'point', 'แต้ม'];

// Tesseract often drops or swaps Thai tone marks and upper/lower vowels,
// so keywords are compared without them
const bare = s => s.replace(/[ัิ-ฺ็-๎]/g, '');
const has = (text, words) => { const t = bare(text); return words.some(w => t.includes(bare(w))); };
const round = v => Math.round(v * 100) / 100;

/** "1,250.00" and "1.250,50" style numbers */
export function toNumber(s) {
  const t = s.replace(/ /g, '');
  const lastSep = Math.max(t.lastIndexOf('.'), t.lastIndexOf(','));
  if (lastSep >= 0 && t.length - lastSep - 1 <= 2) {
    return parseFloat(t.slice(0, lastSep).replace(/[.,]/g, '') + '.' + t.slice(lastSep + 1));
  }
  return parseFloat(t.replace(/[.,]/g, ''));
}

export function isBalanced(items, service, vat, discount, total) {
  if (!items.length || total <= 0) return false;
  const sum = items.reduce((s, i) => s + i.price, 0) + service + vat - discount;
  return Math.abs(sum - total) <= 1.0;
}

function toItem(label, price) {
  let qty = 1, name = label, m;
  if ((m = label.match(QTY_AT_START))) { qty = +m[1]; name = m[2]; }
  else if ((m = label.match(QTY_AFTER))) { name = m[1]; qty = +m[2]; }
  // drop stray unit-price columns before the line total
  name = name.replace(/\s+\d+[.,]\d{2}$/, '').replace(/[|_*]+/g, '').trim()
    .replace(/^[^\p{L}\p{N}]+/u, '');   // OCR noise before the name, e.g. "%แซนวิช"
  return { name, qty: Math.max(qty, 1), price: round(price) };
}

export function parseReceipt(text) {
  const items = [];
  let service = 0, vat = 0, discount = 0, total = 0, vatIncluded = false;

  for (const raw of text.split(/\r?\n/)) {
    // OCR writes sara am as nikhahit + sara aa; join them back
    const line = raw.trim().replace(/ํา/g, 'ำ').replace(/\s{2,}/g, ' ');
    if (!line) continue;
    const lower = line.toLowerCase();
    if (lower.includes('include') || lower.includes('รวมภาษี') || lower.includes('รวม vat')) vatIncluded = true;

    const pm = line.match(PRICE_AT_END);
    if (!pm) continue;
    const price = toNumber(pm[1]);
    const label = line.slice(0, pm.index).trim();
    if (!label) continue;
    const l = label.toLowerCase() + ' ';

    if (has(l, IGNORE)) continue;
    if (has(l, DISCOUNT)) { discount += Math.abs(price); continue; }
    if (has(l, SERVICE)) { service += price; continue; }
    if (has(l, VAT)) { if (!vatIncluded) vat += price; continue; }
    if (has(l, ['subtotal', 'sub total', 'sub-total'])) continue;
    if (has(l, TOTAL)) { total = price; continue; }
    // a bare "รวม" only counts at the start, so dishes like "ข้าวผัดรวมมิตร" stay items
    if (has(l, SUBTOTAL) || l.startsWith('รวม')) continue;
    if (price <= 0) continue;
    items.push(toItem(label, price));
  }

  return {
    items, serviceCharge: round(service), vat: round(vat), discount: round(discount), total: round(total),
    source: 'ocr', balanced: isBalanced(items, service, vat, discount, total),
  };
}

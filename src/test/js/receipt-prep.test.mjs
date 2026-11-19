// Run: node --test src/test/js
import test from 'node:test';
import assert from 'node:assert/strict';
import { prepareReceipt, binarize, textSkew, rotate } from '../../main/resources/static/js/receipt-prep.js';

// dark table, white paper in the middle, black "text" lines across the paper
function photo(w, h) {
  const g = new Uint8ClampedArray(w * h).fill(40);
  for (let y = 100; y < h - 100; y++) for (let x = 150; x < w - 150; x++)
    g[y * w + x] = (y % 30 < 6 && x > 180 && x < w - 180) ? 20 : 235;
  return g;
}
const toRgba = g => { const a = new Uint8ClampedArray(g.length * 4); g.forEach((v, i) => a.set([v, v, v, 255], i * 4)); return a; };

test('crops to the paper and drops the table', () => {
  const p = prepareReceipt(toRgba(photo(600, 900)), 600, 900, { outWidth: 300 });
  assert.equal(p.w, 300);
  const corners = [p.gray[0], p.gray[p.w - 1], p.gray[(p.h - 1) * p.w]];
  assert.ok(corners.every(v => v > 200), 'table outside the paper is blanked white');
});

test('finds and undoes a tilt of the text lines', () => {
  const w = 600, h = 900, tilted = rotate(photo(w, h), w, h, 6);
  const skew = textSkew(binarize(tilted, w, h), w, h);
  assert.ok(Math.abs(Math.abs(skew) - 6) <= 1, `skew ${skew}`);
  const back = rotate(tilted, w, h, skew);
  assert.ok(Math.abs(textSkew(binarize(back, w, h), w, h)) <= 1);
});

test('no paper in the picture gives null', () => {
  assert.equal(prepareReceipt(toRgba(new Uint8ClampedArray(400 * 400).fill(30)), 400, 400), null);
});

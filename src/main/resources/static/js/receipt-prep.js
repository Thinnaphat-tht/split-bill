// Camera photo cleanup before phone OCR: find the white paper, cut away the table around it,
// straighten it and make it black and white. Works on canvas RGBA pixels, no libraries.

export function prepareReceipt(rgba, w, h, { outWidth = 1400 } = {}) {
  const gray = new Uint8ClampedArray(w * h);
  for (let i = 0; i < w * h; i++) gray[i] = (rgba[4 * i] * 299 + rgba[4 * i + 1] * 587 + rgba[4 * i + 2] * 114) / 1000;
  // 1. small averaged copy scored for "white paper": bright and colourless (skin and wood are bright but coloured)
  const s = 8, sw = Math.floor(w / s), sh = Math.floor(h / s);
  const small = new Float32Array(sw * sh);
  for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) {
    let sum = 0;
    for (let dy = 0; dy < s; dy++) for (let dx = 0; dx < s; dx++) {
      const i = 4 * ((y * s + dy) * w + x * s + dx), r = rgba[i], g = rgba[i + 1], b = rgba[i + 2];
      sum += Math.max(0, (r * 299 + g * 587 + b * 114) / 1000 - 2 * (Math.max(r, g, b) - Math.min(r, g, b)));
    }
    small[y * sw + x] = sum / (s * s);
  }
  const t = otsu(small);
  // 2. largest bright connected blob
  const label = new Int32Array(sw * sh).fill(-1);
  let best = null;
  const stack = new Int32Array(sw * sh);
  for (let i = 0; i < sw * sh; i++) {
    if (label[i] !== -1 || small[i] < t) continue;
    let top = 0, n = 0; stack[top++] = i; label[i] = i;
    const px = [];
    while (top) {
      const p = stack[--top]; px.push(p); n++;
      const x = p % sw, y = (p / sw) | 0;
      for (const q of [x > 0 ? p - 1 : -1, x < sw - 1 ? p + 1 : -1, y > 0 ? p - sw : -1, y < sh - 1 ? p + sw : -1]) {
        if (q >= 0 && label[q] === -1 && small[q] >= t) { label[q] = i; stack[top++] = q; }
      }
    }
    if (!best || n > best.length) best = px;
  }
  if (!best || best.length < sw * sh * 0.02) return null; // no paper found
  // paper mask: blob cells plus anything between its leftmost and rightmost cell on each row (fills big dark print)
  const mask = new Uint8Array(sw * sh), lo = new Int32Array(sh).fill(sw), hi = new Int32Array(sh).fill(-1);
  for (const p of best) { const x = p % sw, y = (p / sw) | 0; if (x < lo[y]) lo[y] = x; if (x > hi[y]) hi[y] = x; }
  for (let y = 0; y < sh; y++) for (let x = lo[y]; x <= hi[y]; x++) mask[y * sw + x] = 1;
  // shrink by one cell so the paper edge and the background next to it are dropped
  const inside = (x, y) => x >= 0 && y >= 0 && x < sw && y < sh && mask[y * sw + x];
  const core = (x, y) => inside(x, y) && inside(x - 1, y) && inside(x + 1, y) && inside(x, y - 1) && inside(x, y + 1);
  // 3. orientation from second moments of the blob
  let mx = 0, my = 0;
  for (const p of best) { mx += p % sw; my += (p / sw) | 0; }
  mx /= best.length; my /= best.length;
  let cxx = 0, cyy = 0, cxy = 0;
  for (const p of best) { const dx = p % sw - mx, dy = ((p / sw) | 0) - my; cxx += dx * dx; cyy += dy * dy; cxy += dx * dy; }
  let ang = 0.5 * Math.atan2(2 * cxy, cxx - cyy); // long axis angle
  // receipts are tall: text lines run along the short axis
  let ay = [Math.cos(ang), Math.sin(ang)], ax = [-Math.sin(ang), Math.cos(ang)];
  // a short or wide slip: assume the photo is roughly upright rather than turning it on its side
  if (Math.abs(ay[1]) < Math.abs(ay[0])) [ax, ay] = [ay, ax];
  if (ax[0] < 0) ax = [-ax[0], -ax[1]];
  if (ay[1] < 0) ay = [-ay[0], -ay[1]];
  // 4. extent along the axes (in full-size pixels)
  let u0 = Infinity, u1 = -Infinity, v0 = Infinity, v1 = -Infinity;
  for (const p of best) {
    const dx = (p % sw - mx) * s, dy = (((p / sw) | 0) - my) * s;
    const u = dx * ax[0] + dy * ax[1], v = dx * ay[0] + dy * ay[1];
    if (u < u0) u0 = u; if (u > u1) u1 = u; if (v < v0) v0 = v; if (v > v1) v1 = v;
  }
  const pad = s;
  u0 -= pad; u1 += pad; v0 -= pad; v1 += pad;
  const cx = mx * s + s / 2, cy = my * s + s / 2;
  const scale = (u1 - u0) / outWidth;
  const ow = outWidth, oh = Math.round((v1 - v0) / scale);
  // 5. resample the rotated crop (bilinear)
  const out = new Uint8ClampedArray(ow * oh);
  for (let j = 0; j < oh; j++) for (let i = 0; i < ow; i++) {
    const u = u0 + i * scale, v = v0 + j * scale;
    const x = cx + u * ax[0] + v * ay[0], y = cy + u * ax[1] + v * ay[1];
    out[j * ow + i] = core(Math.floor(x / s), Math.floor(y / s)) ? bilinear(gray, w, h, x, y) : 255;
  }
  return { gray: out, w: ow, h: oh, angle: Math.atan2(ax[1], ax[0]) * 180 / Math.PI };
}

// Bradley adaptive threshold: handles shadows and uneven light across the paper
export function binarize(gray, w, h, win = 0, k = 0.15) {
  win = win || Math.max(15, Math.round(w / 40));
  const ii = new Float64Array((w + 1) * (h + 1));
  for (let y = 0; y < h; y++) {
    let row = 0;
    for (let x = 0; x < w; x++) { row += gray[y * w + x]; ii[(y + 1) * (w + 1) + x + 1] = ii[y * (w + 1) + x + 1] + row; }
  }
  const out = new Uint8ClampedArray(w * h), r = win >> 1;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const x0 = Math.max(0, x - r), x1 = Math.min(w, x + r + 1), y0 = Math.max(0, y - r), y1 = Math.min(h, y + r + 1);
    const sum = ii[y1 * (w + 1) + x1] - ii[y0 * (w + 1) + x1] - ii[y1 * (w + 1) + x0] + ii[y0 * (w + 1) + x0];
    out[y * w + x] = gray[y * w + x] * (x1 - x0) * (y1 - y0) < sum * (1 - k) ? 0 : 255;
  }
  return out;
}

function bilinear(g, w, h, x, y) {
  if (x < 0 || y < 0 || x >= w - 1 || y >= h - 1) return 255;
  const x0 = x | 0, y0 = y | 0, fx = x - x0, fy = y - y0, i = y0 * w + x0;
  return (g[i] * (1 - fx) + g[i + 1] * fx) * (1 - fy) + (g[i + w] * (1 - fx) + g[i + w + 1] * fx) * fy;
}

function otsu(a) {
  const hist = new Float64Array(256);
  for (const v of a) hist[Math.min(255, v | 0)]++;
  let total = a.length, sum = 0;
  for (let i = 0; i < 256; i++) sum += i * hist[i];
  let sumB = 0, wB = 0, best = 0, t = 128;
  for (let i = 0; i < 256; i++) {
    wB += hist[i]; if (!wB) continue;
    const wF = total - wB; if (!wF) break;
    sumB += i * hist[i];
    const mB = sumB / wB, mF = (sum - sumB) / wF, between = wB * wF * (mB - mF) ** 2;
    if (between > best) { best = between; t = i; }
  }
  return t;
}

// Text skew from a binarized image: the angle whose row profile has the sharpest peaks
export function textSkew(bin, w, h, maxDeg = 20) {
  const step = 2, pts = [];
  for (let y = 0; y < h; y += step) for (let x = 0; x < w; x += step) if (!bin[y * w + x]) pts.push(x - w / 2, y - h / 2);
  let best = 0, bestScore = -1;
  const rows = new Float64Array(h * 2 + w);
  for (let deg = -maxDeg; deg <= maxDeg; deg += 0.5) {
    const a = deg * Math.PI / 180, sn = Math.sin(a), cs = Math.cos(a);
    rows.fill(0);
    for (let i = 0; i < pts.length; i += 2) rows[Math.round((pts[i + 1] * cs - pts[i] * sn + h + w / 2) / step)]++; // bin by step, or the sampling grid itself peaks at 0°
    let score = 0;
    for (let i = 1; i < rows.length; i++) score += (rows[i] - rows[i - 1]) ** 2;
    if (score > bestScore) { bestScore = score; best = deg; }
  }
  return best;
}

// Rotate a grayscale image by deg around its centre, keeping the size (corners fill white)
export function rotate(gray, w, h, deg) {
  const a = deg * Math.PI / 180, sn = Math.sin(a), cs = Math.cos(a), out = new Uint8ClampedArray(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const dx = x - w / 2, dy = y - h / 2;
    out[y * w + x] = bilinear(gray, w, h, dx * cs - dy * sn + w / 2, dx * sn + dy * cs + h / 2);
  }
  return out;
}

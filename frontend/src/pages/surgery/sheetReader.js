/**
 * Reads the material labels of a consumption sheet (PDF or photos) in the browser.
 *
 * Each label becomes { page, lot, gtin, code } where:
 *  - code: GS1 text of the 2D code (Data Matrix/QR), when the label has one (GTIN, lot and expiry date);
 *  - lot: the lot (from the 2D code or from the 1D lot barcode);
 *  - gtin: the GTIN barcode read beside the lot (only used to tell lots apart).
 * Labels whose GTIN was read but whose lot was not (e.g. barcode cut at the edge of the sheet) come back with
 * lot = null, so the screen asks for the lot.
 *
 * Each page is read at two resolutions (pages may come sideways or slightly tilted) and the GTINs left without
 * a lot are read again in an enlarged crop. Tested with the hospitals' sheets: 43 of 44 labels read.
 */
import * as pdfjs from 'pdfjs-dist';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import { prepareZXingModule, readBarcodes } from 'zxing-wasm/reader';
import zxingWasmUrl from 'zxing-wasm/reader/zxing_reader.wasm?url';

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;
// The .wasm file is served by the application itself (no external CDN)
prepareZXingModule({
  overrides: { locateFile: (path, prefix) => (path.endsWith('.wasm') ? zxingWasmUrl : prefix + path) },
});

const OPTIONS = {
  tryHarder: true, tryRotate: true, tryDownscale: true, maxNumberOfSymbols: 255,
  formats: ['DataMatrix', 'QRCode', 'Code128', 'EAN13'],
};
/** Page widths read (px); the larger pass stays under the canvas limit of phones (~16 million pixels). */
const WIDTHS = [2400, 3200];
const MAX_PIXELS = 15_000_000;
const LOT_PATTERN = /^[A-Z0-9-]{4,20}$/i;

function gs1(text) {
  const out = {};
  const re = /\((\d{2})\)([^(]*)/g;
  let m;
  while ((m = re.exec(text))) out[m[1]] = m[2].trim();
  return out;
}

const center = (p) => ({
  x: (p.topLeft.x + p.topRight.x + p.bottomLeft.x + p.bottomRight.x) / 4,
  y: (p.topLeft.y + p.topRight.y + p.bottomLeft.y + p.bottomRight.y) / 4,
});

function canvasOf(width, height) {
  const c = document.createElement('canvas');
  c.width = Math.round(width);
  c.height = Math.round(height);
  return c;
}

/** Draws the source (PDF page or image) at the given width on a white canvas. */
async function render(source, width) {
  const scale = width / source.width;
  let w = width;
  let h = source.height * scale;
  if (w * h > MAX_PIXELS) {
    const k = Math.sqrt(MAX_PIXELS / (w * h));
    w *= k;
    h *= k;
  }
  const canvas = canvasOf(w, h);
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  if (source.page) {
    const viewport = source.page.getViewport({ scale: canvas.width / source.width });
    await source.page.render({ canvasContext: ctx, viewport }).promise;
  } else {
    ctx.drawImage(source.bitmap, 0, 0, canvas.width, canvas.height);
  }
  return canvas;
}

async function detect(canvas, offset = { x0: 0, y0: 0, scale: 1 }) {
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  const results = await readBarcodes(ctx.getImageData(0, 0, canvas.width, canvas.height), OPTIONS);
  return results.filter((r) => r.isValid !== false).map((r) => {
    const c = center(r.position);
    return {
      format: r.format, text: r.text, orientation: r.orientation || 0,
      x: offset.x0 + c.x / offset.scale, y: offset.y0 + c.y / offset.scale,
    };
  });
}

/** Same code read in more than one pass counts once. */
function merge(list, width) {
  const out = [];
  list.forEach((d) => {
    if (!out.some((o) => o.text === d.text && Math.hypot(o.x - d.x, o.y - d.y) < width * 0.03)) out.push(d);
  });
  return out;
}

/** Puts the coordinates in the reading direction of the labels (page photographed or scanned sideways). */
function upright(dets) {
  const counts = [0, 0, 0, 0];
  dets.filter((d) => d.format === 'Code128' || d.format === 'EAN13')
    .forEach((d) => { counts[Math.round((((d.orientation % 360) + 360) % 360) / 90) % 4] += 1; });
  const q = counts.indexOf(Math.max(...counts));
  const rotate = (x, y) => [[x, y], [y, -x], [-x, -y], [-y, x]][q];
  return dets.map((d) => {
    const [x, y] = rotate(d.x, d.y);
    return { ...d, ux: x, uy: y };
  });
}

/** Groups the codes into labels: 2D code alone, or 1D lot barcode + GTIN barcode on its right. */
function group(dets, W) {
  const labels = [];
  const used = new Set();
  dets.forEach((d, i) => {
    if (d.format !== 'DataMatrix' && d.format !== 'QRCode') return;
    const ai = gs1(d.text);
    if (!ai['10']) return;
    used.add(i);
    labels.push({ lot: ai['10'].toUpperCase(), gtin: ai['01'] || null, code: d.text, ux: d.ux, uy: d.uy, kind: '2D' });
    // the GTIN barcode printed on the same label
    dets.forEach((e, j) => {
      if (e.format === 'EAN13' && !used.has(j) && ai['01'] && ai['01'].endsWith(e.text)
        && Math.hypot(e.ux - d.ux, e.uy - d.uy) < W * 0.25) used.add(j);
    });
  });
  dets.forEach((d, i) => {
    if (d.format !== 'Code128' || used.has(i)) return;
    used.add(i);
    let best = -1;
    let bestDx = Infinity;
    dets.forEach((e, j) => {
      if (e.format !== 'EAN13' || used.has(j)) return;
      const dx = e.ux - d.ux;
      const dy = Math.abs(e.uy - d.uy);
      if (dx > W * 0.05 && dx < W * 0.35 && dy < W * 0.04 && dx < bestDx) { bestDx = dx; best = j; }
    });
    if (best >= 0) used.add(best);
    labels.push({ lot: d.text.toUpperCase(), gtin: best >= 0 ? dets[best].text : null, code: null, ux: d.ux, uy: d.uy, kind: '1D' });
  });
  // tilted labels: a lot still without GTIN takes the nearest free GTIN on its right
  labels.filter((l) => !l.gtin).forEach((l) => {
    let best = -1;
    let bestD = Infinity;
    dets.forEach((e, j) => {
      if (e.format !== 'EAN13' || used.has(j)) return;
      const dx = e.ux - l.ux;
      const dy = Math.abs(e.uy - l.uy);
      if (dx > 0 && dx < W * 0.45 && dy < W * 0.08 && Math.hypot(dx, dy) < bestD) { bestD = Math.hypot(dx, dy); best = j; }
    });
    if (best >= 0) { used.add(best); l.gtin = dets[best].text; }
  });
  const valid = labels.filter((l) => LOT_PATTERN.test(l.lot));
  // GTINs that belong to no label: lot not read (or a misread duplicate of a label's own GTIN)
  const orphans = dets.filter((d, i) => d.format === 'EAN13' && !used.has(i)).filter((o) => !valid.some((l) => {
    const dx = o.ux - l.ux;
    const dy = Math.abs(o.uy - l.uy);
    return (l.kind === '1D' && dx > 0 && dx < W * 0.45 && dy < W * 0.08)
      || (l.kind === '2D' && o.uy < l.uy && Math.hypot(dx, dy) < W * 0.22)
      || (l.gtin && l.gtin.endsWith(o.text) && Math.hypot(dx, dy) < W * 0.3);
  }));
  return { labels: valid, orphans };
}

/** Reads one page (PDF page or image). */
async function readSource(source) {
  const [small, large] = [await render(source, WIDTHS[0]), await render(source, WIDTHS[1])];
  const k = large.width / small.width;
  const W = large.width;
  let dets = [...await detect(large), ...(await detect(small)).map((d) => ({ ...d, x: d.x * k, y: d.y * k }))];
  dets = upright(merge(dets, W));
  const { labels, orphans } = group(dets, W);

  // GTIN without lot: read again an enlarged crop around it (2D code or lot barcode of the same label)
  for (const o of orphans) {
    const x0 = Math.max(0, o.x - W * 0.3);
    const y0 = Math.max(0, o.y - W * 0.25);
    const w = Math.min(large.width - x0, W * 0.55);
    const h = Math.min(large.height - y0, W * 0.5);
    const crop = canvasOf(w * 2, h * 2);
    const ctx = crop.getContext('2d', { willReadFrequently: true });
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(large, x0, y0, w, h, 0, 0, crop.width, crop.height);
    const extra = await detect(crop, { x0, y0, scale: 2 });
    const known = new Set(labels.map((l) => l.lot));
    const found = extra.map((e) => {
      if (e.format === 'Code128' && LOT_PATTERN.test(e.text)) return { lot: e.text.toUpperCase(), code: null };
      const ai = (e.format === 'DataMatrix' || e.format === 'QRCode') ? gs1(e.text) : {};
      return ai['10'] ? { lot: ai['10'].toUpperCase(), code: e.text } : null;
    }).find((f) => f && !known.has(f.lot));
    if (found) {
      labels.push({ ...found, gtin: o.text, ux: o.ux, uy: o.uy, kind: 'crop' });
      o.fixed = true;
    }
  }
  // order of reading: top to bottom, left to right
  labels.sort((a, b) => (Math.abs(a.uy - b.uy) > W * 0.05 ? a.uy - b.uy : a.ux - b.ux));
  return [
    ...labels.map(({ lot, gtin, code }) => ({ lot, gtin, code })),
    ...orphans.filter((o) => !o.fixed).map((o) => ({ lot: null, gtin: o.text, code: null })),
  ];
}

async function* sources(files) {
  for (const file of files) {
    if (file.type === 'application/pdf' || /\.pdf$/i.test(file.name)) {
      const pdf = await pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) }).promise;
      try {
        for (let n = 1; n <= pdf.numPages; n += 1) {
          const page = await pdf.getPage(n);
          const vp = page.getViewport({ scale: 1 });
          yield { page, width: vp.width, height: vp.height };
        }
      } finally {
        pdf.destroy();
      }
    } else {
      const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
      yield { bitmap, width: bitmap.width, height: bitmap.height };
    }
  }
}

/**
 * Reads every page of the files. onProgress(pagesRead) is called after each page.
 * Returns the labels in reading order, with the page number (1-based, counted across files).
 */
export async function readSheet(files, onProgress) {
  const out = [];
  let page = 0;
  for await (const source of sources(files)) {
    page += 1;
    const labels = await readSource(source);
    labels.forEach((l) => out.push({ ...l, page }));
    if (onProgress) onProgress(page);
    if (source.bitmap) source.bitmap.close();
  }
  return out;
}

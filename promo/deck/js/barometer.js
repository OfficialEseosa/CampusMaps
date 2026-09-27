// barometer.js, "Which floor? Ask the barometer." One 10 s clip for a transparent video overlay.
// Pure SVG + global gsap. Everything runs on ONE finite, paused gsap timeline (virtual-clock safe:
// no setTimeout, no Date, no CSS animation, no infinite repeats, no randomness).
// Contract: mount(el, opts) -> { play(), pause(), destroy(), seek(s), duration }
//   opts.aspect: '16:9' (1920x1080, default), '3:2' (2160x1440) or '3:4' (1620x2160, portrait reflow).
// Beats: 0-2 s scene builds, anchor read at the door sets the reference; 2-7 s elevator ride, pressure
// falls about 2.5 hPa and the live floor estimate counts 1..6; 7-9 s estimate stable 1.5 s, commit,
// "Floor 6" spoken and buzzed; 9.5-10 s fade out (fully transparent at 10).
// Numbers follow docs/03-localization.md: EMA alpha 0.15 (about 1 s window at 10 Hz), 0.12 hPa per
// metre, 4.7 m storey = 0.53 hPa, floorEstimate = refFloor - (filtered - refPressure) / floorDelta.

const NS = 'http://www.w3.org/2000/svg';
const C = {
  ink: '#1b1614', clay: '#c67c4e', warm: '#ffb68c', teal: '#7ad0e0',
  gold: '#ffd166', err: '#e05a4e', white: '#ffffff', grey: '#9c9793',
};
const DIM = 'rgba(255,255,255,0.8)';
const PANEL = 'rgba(20,16,14,0.55)';
const DURATION = 10;

const LAYOUTS = {
  '16:9': { W: 1920, H: 1080, mx: 96, my: 54, k: 1, kickSize: 22, kickGap: 74, titleSize: 64, titleLH: 1.15, titleMaxLines: 1,
    chipSize: 24, capSize: 34, capLH: 46, maxScale: 1.2 },
  '3:2': { W: 2160, H: 1440, mx: 108, my: 72, k: 1.125, kickSize: 25, kickGap: 83, titleSize: 72, titleLH: 1.15, titleMaxLines: 1,
    chipSize: 27, capSize: 38, capLH: 52, maxScale: 1.35 },
  '3:4': { W: 1620, H: 2160, mx: 81, my: 108, k: 1.35, kickSize: 30, kickGap: 108, titleSize: 88, titleLH: 1.14, titleMaxLines: 2,
    chipSize: 32, capSize: 40, capLH: 54, maxScale: 1.7, portrait: true },
};

const TITLE = 'Which floor? Ask the barometer.';
const CAPTION = ['Pressure drops about half a hectopascal per floor.', 'The phone counts the floors while the elevator moves.'];
const CHIPS = ['SensorManager TYPE_PRESSURE', 'EMA alpha 0.15', '0.12 hPa per metre', 're-zeroed at every anchor', 'only at elevator or stairs nodes'];

// ---------------------------------------------------------------- simulation (deterministic)
const HZ = 10, ALPHA = 0.15, FLOOR_DELTA = 0.53;
const P_REF = 982.60, DROP = 2.5;           // "about 2.4 hPa" for a 1 to 6 ride; 5 storeys at 0.53 hPa would be 2.65
const T_REF = 1.2;                           // anchor read at the door
const T_RIDE0 = 2.0, T_RIDE1 = 5.8;          // compressed version of the ~20 s ride
const T_BLIP = 4.3, BLIP = 0.2;              // a door / HVAC blip the gate ignores
const smooth = (u) => { u = Math.max(0, Math.min(1, u)); return u * u * (3 - 2 * u); };
const pTrue = (t) => P_REF - DROP * smooth((t - T_RIDE0) / (T_RIDE1 - T_RIDE0));
function simulate() {
  const n = DURATION * HZ + 1, raw = [], filt = [];
  let f = P_REF;
  for (let i = 0; i < n; i++) {
    const t = i / HZ;
    const noise = 0.05 * Math.sin(i * 1.7) + 0.035 * Math.sin(i * 4.3 + 1) + 0.02 * Math.sin(i * 9.1 + 2);
    const blip = t >= T_BLIP && t < T_BLIP + 0.3 ? BLIP : 0;
    const r = pTrue(t) + noise + blip;
    f = i === 0 ? r : f + ALPHA * (r - f);
    raw.push(r); filt.push(f);
  }
  // live floor display: rounded estimate, only counting up during the ride (the gate is open in the elevator)
  const disp = [];
  let best = 1, tRound6 = null;
  for (let i = 0; i < n; i++) {
    const est = 1 - (filt[i] - P_REF) / FLOOR_DELTA;
    const r = Math.max(1, Math.min(6, Math.round(est)));
    best = Math.max(best, r);
    disp.push(best);
    if (best === 6 && tRound6 === null) tRound6 = i / HZ;
  }
  const changes = [];
  for (let i = 1; i < n; i++) if (disp[i] !== disp[i - 1]) changes.push([i / HZ, disp[i]]);
  return { raw, filt, disp, tRound6, changes };
}
const SIM = simulate();
const T_STABLE = SIM.tRound6;            // rounded estimate first reads 6
const T_COMMIT = T_STABLE + 1.5;          // stable for 1.5 s
const at = (arr, t) => {
  const x = Math.max(0, Math.min(arr.length - 1, t * HZ)), i = Math.floor(x), u = x - i;
  return i + 1 < arr.length ? arr[i] + (arr[i + 1] - arr[i]) * u : arr[i];
};

function E(tag, attrs, parent) {
  const n = document.createElementNS(NS, tag);
  if (attrs) for (const k in attrs) n.setAttribute(k, attrs[k]);
  if (parent) parent.appendChild(n);
  return n;
}
function T(parent, x, y, str, attrs) {
  const t = E('text', Object.assign({ x, y, 'text-anchor': 'start', fill: C.white,
    'font-family': 'Sora, sans-serif', 'font-weight': 500, 'font-size': 30 }, attrs || {}), parent);
  t.textContent = str;
  return t;
}
function textLen(t, fallback) {
  try { const w = t.getComputedTextLength(); if (w > 0) return w; } catch (e) { /* not laid out */ }
  return fallback;
}

export function mount(el, opts = {}) {
  const ASPECT = LAYOUTS[opts.aspect] ? opts.aspect : '16:9';
  const LY = LAYOUTS[ASPECT];
  const PORT = !!LY.portrait;
  const LX = LY.mx, LR = LY.W - LY.mx, LW = LR - LX;

  const svg = E('svg', { viewBox: `0 0 ${LY.W} ${LY.H}`, width: '100%', height: '100%',
    preserveAspectRatio: 'xMidYMid meet', role: 'img',
    'aria-label': 'The barometer counts floors during an elevator ride in Classroom South and commits Floor 6' });
  svg.style.display = 'block';
  svg.style.overflow = 'visible';
  el.appendChild(svg);

  const defs = E('defs', null, svg);
  const sh = E('filter', { id: 'bm-shadow', filterUnits: 'userSpaceOnUse', x: -100, y: -100, width: LY.W + 200, height: LY.H + 200 }, defs);
  E('feDropShadow', { dx: 0, dy: 2, stdDeviation: 4, 'flood-color': '#000', 'flood-opacity': 0.45 }, sh);
  const glow = E('filter', { id: 'bm-glow', x: '-50%', y: '-50%', width: '200%', height: '200%' }, defs);
  E('feGaussianBlur', { stdDeviation: 5, result: 'b' }, glow);
  const fm = E('feMerge', null, glow);
  E('feMergeNode', { in: 'b' }, fm);
  E('feMergeNode', { in: 'SourceGraphic' }, fm);

  const root = E('g', { filter: 'url(#bm-shadow)' }, svg);
  const hidden = [];
  const hide = (elx, vars) => { hidden.push([elx, vars || { opacity: 0 }]); return elx; };
  const layouts = [];
  const L = (fn) => { fn(); layouts.push(fn); };

  // ---- measuring + wrapping (same rules as explainer.js) ----
  const meas = E('text', { x: 0, y: -1000, opacity: 0, 'font-family': 'Sora, sans-serif' }, svg);
  function measure(str, size, weight) {
    meas.setAttribute('font-size', size); meas.setAttribute('font-weight', weight); meas.textContent = str;
    return textLen(meas, str.length * size * 0.58);
  }
  function wrapLines(pref, size, weight, maxW, maxLines) {
    for (let tries = 0; tries < 14; tries++) {
      if (pref.length <= maxLines && pref.every(l => measure(l, size, weight) <= maxW)) return { lines: pref, size };
      const words = pref.join(' ').split(' ');
      const out = [];
      let cur = '';
      for (const w of words) {
        const t = cur ? cur + ' ' + w : w;
        if (cur && measure(t, size, weight) > maxW) { out.push(cur); cur = w; } else cur = t;
      }
      if (cur) out.push(cur);
      if (out.length <= maxLines && out.every(l => measure(l, size, weight) <= maxW)) return { lines: out, size };
      size *= 0.94;
    }
    return { lines: pref, size };
  }
  function setLines(t, lines, x, y0, lh) {
    while (t.firstChild) t.removeChild(t.firstChild);
    lines.forEach((l, i) => { const ts = E('tspan', { x, y: (y0 + i * lh).toFixed(1) }, t); ts.textContent = l; });
  }

  // ---- header, chips, caption ----
  const kick = hide(T(root, LX, 0, 'HOW CAMPUSMAPS WORKS', { 'font-size': LY.kickSize, 'font-weight': 600, fill: C.warm, 'letter-spacing': 3 * LY.k }),
    { opacity: 0, y: 20 * LY.k });
  const ttl = hide(T(root, LX, 0, '', { 'font-size': LY.titleSize, 'font-weight': 700 }), { opacity: 0, y: 44 * LY.k });
  const artWrap = E('g', null, root);
  const art = E('g', null, artWrap);
  const cg = E('g', null, root);
  const cc = LY.chipSize / 24;
  const chipItems = CHIPS.map(s => {
    const outer = E('g', null, cg);
    const g = E('g', null, outer);
    const r = E('rect', { x: 0, y: 0, height: 48 * cc, rx: 24 * cc, fill: PANEL, stroke: 'rgba(255,182,140,0.55)', 'stroke-width': 1.8 * cc }, g);
    E('circle', { cx: 24 * cc, cy: 24 * cc, r: 5 * cc, fill: C.clay }, g);
    const t = T(g, 40 * cc, 33 * cc, s, { 'font-size': LY.chipSize, 'font-weight': 500 });
    hide(g, { opacity: 0, y: 14 * LY.k });
    return { g, outer, r, t, s, w: 0 };
  });
  const cap = hide(E('g', null, root), { opacity: 0, y: 16 * LY.k });
  const capT = T(cap, LX, 0, '', { 'font-size': LY.capSize, 'font-weight': 500, fill: 'rgba(255,255,255,0.94)' });

  // ================================================================ scene (authored in its own units)
  // Building B: 0..600 x 0..625. Readout R: 0..RW x 0..360. Floor block F: see FL below.
  const RW = PORT ? 1200 : 1060;
  const POS = PORT ? { B: [0, 0], F: [640, 0], R: [0, 660] } : { B: [0, 0], R: [660, 0], F: [660, 382] };
  const REGION = PORT ? [0, 0, 1200, 1020] : [0, 0, 1720, 625];
  const gB = E('g', { transform: `translate(${POS.B})` }, art);
  const gR = E('g', { transform: `translate(${POS.R})` }, art);
  const gF = E('g', { transform: `translate(${POS.F})` }, art);

  // ---- building cross-section: Classroom South, floors 1..6 ----
  const BX0 = 110, BX1 = 440, Y1 = 560, ST = 88, ROOF = Y1 - 6 * ST + 16;
  const SH0 = 330, SH1 = 400, SHC = (SH0 + SH1) / 2;
  const floorY = (i) => Y1 - (i - 1) * ST;           // top of slab i (the floor you stand on)
  const b = {};
  b.frame = hide(E('g', null, gB));
  T(b.frame, BX0, 22, 'Classroom South', { 'font-size': 24, 'font-weight': 600, fill: DIM });
  E('rect', { x: BX0, y: ROOF, width: BX1 - BX0, height: Y1 + 10 - ROOF, fill: 'rgba(255,255,255,0.04)', stroke: 'rgba(255,255,255,0.45)', 'stroke-width': 2.5 }, b.frame);
  E('rect', { x: SH0, y: ROOF, width: SH1 - SH0, height: Y1 - ROOF, fill: 'rgba(122,208,224,0.08)', stroke: 'rgba(122,208,224,0.55)', 'stroke-width': 2, 'stroke-dasharray': '6 6' }, b.frame);
  T(b.frame, SHC, ROOF - 12, 'EL', { 'font-size': 22, 'font-weight': 700, fill: C.teal, 'text-anchor': 'middle' });
  E('line', { x1: 60, y1: Y1 + 10, x2: 480, y2: Y1 + 10, stroke: 'rgba(255,255,255,0.55)', 'stroke-width': 3, 'stroke-linecap': 'round' }, b.frame);
  b.slabs = []; b.labOn = [];
  for (let i = 1; i <= 6; i++) {
    const y = floorY(i);
    const s = hide(E('rect', { x: BX0, y: y, width: BX1 - BX0, height: 8, fill: 'rgba(255,255,255,0.55)' }, gB), { opacity: 0, scaleX: 0, transformOrigin: '0% 50%' });
    b.slabs.push(s);
    T(gB, 92, y - ST / 2 + 12, String(i), { 'font-size': 30, 'font-weight': 700, fill: 'rgba(255,255,255,0.55)', 'text-anchor': 'end' });
    b.labOn.push(hide(T(gB, 92, y - ST / 2 + 12, String(i), { 'font-size': 30, 'font-weight': 700, fill: C.warm, 'text-anchor': 'end' })));
  }
  b.top = hide(E('rect', { x: BX0, y: floorY(6), width: BX1 - BX0, height: 8, fill: C.clay, filter: 'url(#bm-glow)' }, gB));
  // storey dimension (floor 1 to 2)
  b.dim = hide(E('g', null, gB));
  E('path', { d: `M452,${floorY(2)} L462,${floorY(2)} L462,${floorY(1)} L452,${floorY(1)}`, fill: 'none', stroke: C.gold, 'stroke-width': 2.5, 'stroke-linecap': 'round' }, b.dim);
  T(b.dim, 474, floorY(1) - 50, '4.7 m', { 'font-size': 22, 'font-weight': 700, fill: C.gold });
  T(b.dim, 474, floorY(1) - 22, '0.53 hPa', { 'font-size': 22, 'font-weight': 600, fill: C.gold });
  // door + sign at floor 1
  E('rect', { x: BX0 - 3, y: floorY(1) - 62, width: 6, height: 62, fill: C.ink }, b.frame);
  b.sign = hide(E('g', null, gB));
  E('rect', { x: 124, y: floorY(1) - 70, width: 46, height: 26, rx: 4, fill: 'rgba(255,255,255,0.1)', stroke: C.white, 'stroke-width': 2 }, b.sign);
  T(b.sign, 147, floorY(1) - 50, 'CS', { 'font-size': 16, 'font-weight': 700, 'text-anchor': 'middle' });
  {
    const x0 = 116, x1 = 178, y0 = floorY(1) - 78, y1 = floorY(1) - 36, q = 12;
    b.scan = hide(E('path', { d: `M${x0},${y0 + q} L${x0},${y0} L${x0 + q},${y0} M${x1 - q},${y0} L${x1},${y0} L${x1},${y0 + q} ` +
      `M${x0},${y1 - q} L${x0},${y1} L${x0 + q},${y1} M${x1 - q},${y1} L${x1},${y1} L${x1},${y1 - q}`,
    fill: 'none', stroke: C.teal, 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, gB), { opacity: 0, scale: 1.4, transformOrigin: '50% 50%' });
  }
  // anchor chip under the building
  b.chip = hide(E('g', null, gB), { opacity: 0, y: 8 });
  const achR = E('rect', { x: 70, y: Y1 + 26, height: 44, rx: 22, fill: 'rgba(20,40,44,0.85)', stroke: C.teal, 'stroke-width': 2.2 }, b.chip);
  E('circle', { cx: 94, cy: Y1 + 48, r: 6, fill: C.teal }, b.chip);
  const achT = T(b.chip, 110, Y1 + 56, 'anchor read, reference set', { 'font-size': 22, 'font-weight': 600 });
  L(() => achR.setAttribute('width', textLen(achT, 300) + 60));
  // elevator car + you
  b.car = E('g', null, gB);
  const carG = hide(E('g', null, b.car));
  E('rect', { x: SH0 + 6, y: Y1 - 72, width: SH1 - SH0 - 12, height: 72, rx: 6, fill: 'rgba(122,208,224,0.2)', stroke: C.teal, 'stroke-width': 3 }, carG);
  b.you = E('circle', { cx: 0, cy: 0, r: 13, fill: C.white, stroke: C.clay, 'stroke-width': 5, filter: 'url(#bm-glow)', opacity: 0 }, gB);
  b.youTx = E('text', { x: 0, y: 0, 'text-anchor': 'middle', 'font-family': 'Sora, sans-serif', 'font-size': 20, 'font-weight': 700, fill: C.warm, opacity: 0 }, gB);
  b.youTx.textContent = 'you';

  // ---- readout R ----
  const r = {};
  r.g = hide(E('g', null, gR), { opacity: 0, y: 14 });
  T(r.g, 0, 26, 'pressure, filtered', { 'font-size': 26, fill: DIM });
  r.num = T(r.g, 0, 126, P_REF.toFixed(2), { 'font-size': 96, 'font-weight': 700, 'text-anchor': 'end', style: 'font-variant-numeric: tabular-nums' });
  r.unit = T(r.g, 0, 126, 'hPa', { 'font-size': 40, 'font-weight': 600, fill: DIM });
  L(() => {
    const w = measure('988.88', 96, 700);
    r.num.setAttribute('x', w.toFixed(1)); r.unit.setAttribute('x', (w + 16).toFixed(1));
  });
  // legend top right
  const lg = E('g', null, r.g);
  E('line', { x1: RW - 330, y1: 18, x2: RW - 296, y2: 18, stroke: 'rgba(255,255,255,0.55)', 'stroke-width': 2 }, lg);
  T(lg, RW - 286, 26, 'raw', { 'font-size': 22, fill: DIM });
  E('line', { x1: RW - 216, y1: 18, x2: RW - 182, y2: 18, stroke: C.clay, 'stroke-width': 5, 'stroke-linecap': 'round' }, lg);
  T(lg, RW - 172, 26, 'filtered (EMA)', { 'font-size': 22, fill: DIM });
  r.ref = hide(T(gR, 0, 172, `reference ${P_REF.toFixed(2)} hPa at floor 1`, { 'font-size': 26, 'font-weight': 600, fill: C.teal }), { opacity: 0, x: -10 });
  // trace box
  const TY0 = 196, TY1 = 360, PMAX = 982.85, PMIN = 979.95;
  const py = (p) => TY0 + 10 + (PMAX - p) / (PMAX - PMIN) * (TY1 - TY0 - 20);
  r.box = hide(E('g', null, gR));
  E('rect', { x: 0, y: TY0, width: RW, height: TY1 - TY0, rx: 12, fill: 'rgba(20,16,14,0.45)', stroke: 'rgba(255,255,255,0.25)', 'stroke-width': 1.5 }, r.box);
  const tclip = E('clipPath', { id: 'bm-trace' }, defs);
  E('rect', { x: 2, y: TY0 + 2, width: RW - 4, height: TY1 - TY0 - 4, rx: 10 }, tclip);
  const tg = E('g', { 'clip-path': 'url(#bm-trace)' }, r.box);
  r.refLine = hide(E('line', { x1: 0, y1: py(P_REF), x2: RW, y2: py(P_REF), stroke: 'rgba(122,208,224,0.45)', 'stroke-width': 1.5, 'stroke-dasharray': '5 7' }, tg));
  r.raw = E('path', { d: '', fill: 'none', stroke: 'rgba(255,255,255,0.55)', 'stroke-width': 2, 'stroke-linejoin': 'round' }, tg);
  r.filt = E('path', { d: '', fill: 'none', stroke: C.clay, 'stroke-width': 5, 'stroke-linejoin': 'round', 'stroke-linecap': 'round', filter: 'url(#bm-glow)' }, tg);
  r.blipG = E('g', { opacity: 0 }, gR);
  r.blipLine = E('line', { x1: 0, y1: 0, x2: 0, y2: 0, stroke: C.gold, 'stroke-width': 2, 'stroke-dasharray': '3 4' }, r.blipG);
  r.blipTx = T(r.blipG, 0, TY0 + 34, '0.2 hPa door blip, ignored', { 'font-size': 22, 'font-weight': 600, fill: C.gold, 'text-anchor': 'middle' });
  let blipHalf = 150;
  L(() => { blipHalf = textLen(r.blipTx, 290) / 2 + 8; });
  const WIN = 6;   // seconds visible in the scrolling trace

  // ---- floor block F ----
  const FL = PORT
    ? { num: [0, 232], stable: [196, 120], form: [196, 196], bub: [0, 330, 250, 92], watch: [430, 430] }
    : { num: [0, 222], stable: [196, 116], form: [196, 190], bub: [620, 26, 236, 92], watch: [960, 128] };
  const f = {};
  f.g = hide(E('g', null, gF), { opacity: 0, y: 14 });
  T(f.g, 0, 26, 'floor estimate', { 'font-size': 26, fill: DIM });
  f.numWrap = E('g', { transform: `translate(${FL.num[0]},${FL.num[1]})` }, f.g);
  f.num = T(f.numWrap, 0, 0, '?', { 'font-size': 190, 'font-weight': 700 });
  f.numDone = hide(T(f.numWrap, 0, 0, '6', { 'font-size': 190, 'font-weight': 700, fill: C.gold }));
  f.form = T(f.g, FL.form[0], FL.form[1], '1 − (p − ref) ÷ 0.53', { 'font-size': 26, fill: DIM });
  // stable indicator
  f.stable = hide(E('g', null, E('g', { transform: `translate(${FL.stable[0]},${FL.stable[1]})` }, gF)), { opacity: 0, x: -10 });
  E('circle', { cx: 20, cy: -9, r: 18, fill: 'none', stroke: 'rgba(255,255,255,0.25)', 'stroke-width': 4 }, f.stable);
  const CIRC = 2 * Math.PI * 18;
  f.ring = E('circle', { cx: 20, cy: -9, r: 18, fill: 'none', stroke: C.clay, 'stroke-width': 4, transform: 'rotate(-90 20 -9)', 'stroke-linecap': 'round' }, f.stable);
  f.ring.style.strokeDasharray = `${CIRC} ${CIRC}`;
  hide(f.ring, { strokeDashoffset: CIRC });
  f.check = hide(E('path', { d: 'M11,-9 L18,-2 L30,-16', fill: 'none', stroke: C.gold, 'stroke-width': 4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, f.stable));
  T(f.stable, 52, 0, 'stable 1.5 s', { 'font-size': 26, 'font-weight': 600 });
  // speech bubble, tail on the right pointing at the watch
  {
    const [bx, by, bw, bh] = FL.bub;
    f.bubble = hide(E('g', null, gF), { opacity: 0, scale: 0.6, transformOrigin: '100% 50%' });
    const my = by + bh / 2;
    E('path', { d: `M${bx + 20},${by} L${bx + bw - 20},${by} Q${bx + bw},${by} ${bx + bw},${by + 20} L${bx + bw},${my - 12} L${bx + bw + 24},${my} L${bx + bw},${my + 12} ` +
      `L${bx + bw},${by + bh - 20} Q${bx + bw},${by + bh} ${bx + bw - 20},${by + bh} L${bx + 20},${by + bh} Q${bx},${by + bh} ${bx},${by + bh - 20} L${bx},${by + 20} Q${bx},${by} ${bx + 20},${by} Z`,
    fill: 'rgba(20,40,44,0.8)', stroke: C.teal, 'stroke-width': 2.8, 'stroke-linejoin': 'round' }, f.bubble);
    T(f.bubble, bx + bw / 2, by + bh / 2 + 15, 'Floor 6', { 'font-size': 42, 'font-weight': 700, 'text-anchor': 'middle' });
  }
  // watch
  {
    const [wx, wy] = FL.watch, wr = 64;
    f.watch = hide(E('g', null, gF), { opacity: 0, y: 14 });
    f.rings = [0, 1, 2, 3].map(() => E('circle', { cx: wx, cy: wy, r: wr, fill: 'none', stroke: C.gold, 'stroke-width': 3.5, opacity: 0 }, f.watch));
    E('rect', { x: wx - 27, y: wy - wr - 40, width: 54, height: 40, rx: 9, fill: 'rgba(255,255,255,0.18)' }, f.watch);
    E('rect', { x: wx - 27, y: wy + wr, width: 54, height: 40, rx: 9, fill: 'rgba(255,255,255,0.18)' }, f.watch);
    E('circle', { cx: wx, cy: wy, r: wr, fill: '#241d1a', stroke: 'rgba(255,255,255,0.5)', 'stroke-width': 5 }, f.watch);
    E('circle', { cx: wx, cy: wy, r: wr - 13, fill: C.ink, stroke: 'rgba(255,255,255,0.85)', 'stroke-width': 2.5 }, f.watch);
    f.wnum = hide(T(f.watch, wx, wy + 17, '6', { 'font-size': 48, 'font-weight': 700, fill: C.gold, 'text-anchor': 'middle' }), { opacity: 0, scale: 0.5, transformOrigin: '50% 50%' });
  }

  // ---- per-frame scene state, driven by one proxy tween ----
  const DOOR_X = 205, WALK1 = 1.3, WALK2 = 2.0, SHOW = 0.6;
  function scene(t) {
    const fv = at(SIM.filt, t);
    r.num.textContent = fv.toFixed(2);
    f.num.textContent = t < T_REF ? '?' : String(SIM.disp[Math.min(SIM.disp.length - 1, Math.floor(t * HZ))]);
    // car and you
    const p = (P_REF - pTrue(t)) / DROP, lift = p * 5 * ST;
    b.car.setAttribute('transform', `translate(0,${(-lift).toFixed(2)})`);
    let yx, yy;
    if (t < WALK1) { yx = DOOR_X; yy = Y1 - 24; } else if (t < WALK2) { yx = DOOR_X + (SHC - DOOR_X) * smooth((t - WALK1) / (WALK2 - WALK1)); yy = Y1 - 24; } else { yx = SHC; yy = Y1 - 30 - lift; }
    const yo = t < SHOW ? 0 : Math.min(1, (t - SHOW) / 0.3);
    b.you.setAttribute('cx', yx.toFixed(2)); b.you.setAttribute('cy', yy.toFixed(2)); b.you.setAttribute('opacity', yo.toFixed(3));
    b.youTx.setAttribute('x', yx.toFixed(2)); b.youTx.setAttribute('y', (yy - 24).toFixed(2));
    b.youTx.setAttribute('opacity', (t < WALK2 ? yo : Math.max(0, 1 - (t - WALK2) / 0.3)).toFixed(3));
    // scrolling trace: the last WIN seconds, newest at the right edge
    const px = (ts) => RW - (t - ts) * RW / WIN;
    let dr = '', df = '';
    const i0 = Math.max(0, Math.ceil((t - WIN) * HZ)), i1 = Math.floor(t * HZ);
    for (let i = i0; i <= i1; i++) {
      const x = px(i / HZ).toFixed(1);
      dr += (dr ? ' L' : 'M') + x + ',' + py(SIM.raw[i]).toFixed(1);
      df += (df ? ' L' : 'M') + x + ',' + py(SIM.filt[i]).toFixed(1);
    }
    if (dr) { dr += ` L${RW},${py(at(SIM.raw, t)).toFixed(1)}`; df += ` L${RW},${py(fv).toFixed(1)}`; }
    r.raw.setAttribute('d', dr); r.filt.setAttribute('d', df);
    // blip label follows the blip as it scrolls
    const bt = T_BLIP + 0.1, bo = t < bt + 0.3 ? 0 : Math.min(1, (t - bt - 0.3) / 0.3);
    const bxp = px(bt), byp = py(at(SIM.raw, bt + 0.05));
    const lx = Math.max(blipHalf, Math.min(RW - blipHalf, bxp));
    r.blipTx.setAttribute('x', lx.toFixed(1));
    r.blipLine.setAttribute('x1', bxp.toFixed(1)); r.blipLine.setAttribute('y1', (TY0 + 44).toFixed(1));
    r.blipLine.setAttribute('x2', bxp.toFixed(1)); r.blipLine.setAttribute('y2', (byp - 8).toFixed(1));
    r.blipG.setAttribute('opacity', bo.toFixed(3));
  }

  // ---- frame layout (header, chips, caption, art box) ----
  function layoutFrame() {
    const k = LY.k;
    const kickY = LY.my + 58 * k, titleY = kickY + LY.kickGap;
    kick.setAttribute('y', kickY);
    const tw = wrapLines([TITLE], LY.titleSize, 700, LW, LY.titleMaxLines);
    ttl.setAttribute('font-size', tw.size.toFixed(1));
    const tlh = tw.size * LY.titleLH;
    setLines(ttl, tw.lines, LX, titleY, tlh);
    const titleBottom = titleY + (tw.lines.length - 1) * tlh + tw.size * 0.28;
    const capLast = LY.H - LY.my - 12 * k;
    const cw = wrapLines(CAPTION, LY.capSize, 500, LW, 4);
    capT.setAttribute('font-size', cw.size.toFixed(1));
    const capFirst = capLast - (cw.lines.length - 1) * LY.capLH;
    setLines(capT, cw.lines, LX, capFirst, LY.capLH);
    const chH = 48 * cc, rowStep = 60 * cc, gap = 14 * cc;
    for (const it of chipItems) { it.w = textLen(it.t, it.s.length * LY.chipSize * 0.6) + 62 * cc; it.r.setAttribute('width', it.w); }
    const rows = [[]]; let x = 0;
    for (const it of chipItems) {
      if (x > 0 && x + it.w > LW) { rows.push([]); x = 0; }
      rows[rows.length - 1].push(it); x += it.w + gap;
    }
    const chipsBottom = capFirst - 66 * k;
    rows.forEach((row, ri) => {
      const y = chipsBottom - chH - (rows.length - 1 - ri) * rowStep;
      let cx = LX;
      for (const it of row) { it.outer.setAttribute('transform', `translate(${cx.toFixed(1)},${y.toFixed(1)})`); cx += it.w + gap; }
    });
    const chipsTop = chipsBottom - chH - (rows.length - 1) * rowStep;
    const box = [LX, titleBottom + 40 * k, LR, chipsTop - 40 * k];
    const rw = REGION[2] - REGION[0], rh = REGION[3] - REGION[1], bw = box[2] - box[0], bh = box[3] - box[1];
    const sc = Math.min(bw / rw, bh / rh, LY.maxScale);
    const tx = box[0] + (bw - rw * sc) / 2 - REGION[0] * sc, ty = box[1] + (bh - rh * sc) / 2 - REGION[1] * sc;
    artWrap.setAttribute('transform', `translate(${tx.toFixed(2)},${ty.toFixed(2)}) scale(${sc.toFixed(4)})`);
  }
  L(layoutFrame);

  // ================================================================ timeline
  let ctx = null, master = null;
  function build() {
    for (const [elx, vars] of hidden) gsap.set(elx, vars);
    gsap.set(root, { opacity: 0 });
    scene(0);
    master = gsap.timeline({ paused: true });
    const tl = master;
    tl.to(root, { opacity: 1, duration: 0.4, ease: 'power1.out' }, 0);
    tl.to(kick, { opacity: 1, y: 0, duration: 0.5, ease: 'power3.out' }, 0.2);
    tl.to(ttl, { opacity: 1, y: 0, duration: 0.65, ease: 'power3.out' }, 0.26);
    tl.to(cap, { opacity: 1, y: 0, duration: 0.6, ease: 'power2.out' }, 1.0);
    tl.to(chipItems.map(c => c.g), { opacity: 1, y: 0, duration: 0.45, stagger: 0.12, ease: 'back.out(1.6)' }, 1.3);

    // scene builds
    tl.to(b.frame, { opacity: 1, duration: 0.5 }, 0.35);
    tl.to(b.slabs, { opacity: 1, scaleX: 1, duration: 0.35, stagger: 0.07, ease: 'power2.out' }, 0.45);
    tl.to(carG, { opacity: 1, duration: 0.3 }, 0.9);
    tl.to(b.dim, { opacity: 1, duration: 0.4 }, 1.4);
    tl.to(r.g, { opacity: 1, y: 0, duration: 0.5, ease: 'power2.out' }, 0.5);
    tl.to(r.box, { opacity: 1, duration: 0.4 }, 0.6);
    tl.to(f.g, { opacity: 1, y: 0, duration: 0.5, ease: 'power2.out' }, 0.7);
    tl.to(f.watch, { opacity: 1, y: 0, duration: 0.5, ease: 'power2.out' }, 0.85);
    // anchor read at the door: reference set
    tl.to(b.sign, { opacity: 1, duration: 0.3 }, 0.6);
    tl.to(b.scan, { opacity: 1, scale: 1, duration: 0.35, ease: 'power2.out' }, T_REF - 0.3);
    tl.to(b.chip, { opacity: 1, y: 0, duration: 0.3, ease: 'back.out(2)' }, T_REF - 0.1);
    tl.to(r.ref, { opacity: 1, x: 0, duration: 0.35 }, T_REF);
    tl.to(r.refLine, { opacity: 1, duration: 0.3 }, T_REF);
    tl.to(b.scan, { opacity: 0, duration: 0.4 }, T_REF + 0.9);
    tl.to(b.chip, { opacity: 0, duration: 0.4 }, 2.7);

    // the whole live scene: readout, trace, floor count, car, you
    const clock = { t: 0 };
    tl.to(clock, { t: DURATION, duration: DURATION, ease: 'none', onUpdate: () => scene(clock.t) }, 0);
    // floor count pops and building labels light as each floor is reached
    for (const [tc, fl] of SIM.changes) {
      tl.fromTo(f.numWrap, { scale: 1.12, transformOrigin: '0% 100%' }, { scale: 1, duration: 0.3, ease: 'power2.out', immediateRender: false }, tc);
      tl.to(b.labOn[fl - 1], { opacity: 1, duration: 0.2 }, tc);
    }
    tl.to(b.labOn[0], { opacity: 1, duration: 0.3 }, T_REF);

    // commit: stable 1.5 s, then speak and buzz
    tl.to(f.stable, { opacity: 1, x: 0, duration: 0.3 }, T_STABLE);
    tl.to(f.ring, { strokeDashoffset: 0, duration: 1.5, ease: 'none' }, T_STABLE);
    tl.to(f.check, { opacity: 1, duration: 0.2 }, T_COMMIT);
    tl.to(f.numDone, { opacity: 1, duration: 0.25 }, T_COMMIT);
    tl.to(f.num, { opacity: 0, duration: 0.25 }, T_COMMIT);
    tl.to(b.top, { opacity: 1, duration: 0.3 }, T_COMMIT);
    tl.to(f.bubble, { opacity: 1, scale: 1, duration: 0.45, ease: 'back.out(1.8)' }, T_COMMIT + 0.1);
    tl.to(f.wnum, { opacity: 1, scale: 1, duration: 0.35, ease: 'back.out(2)' }, T_COMMIT + 0.15);
    [0.2, 0.42, 0.95, 1.17].forEach((d, i) => {
      tl.fromTo(f.rings[i], { attr: { r: 64 }, opacity: 0.9 }, { attr: { r: 112 }, opacity: 0, duration: 0.85, ease: 'power2.out', immediateRender: false }, T_COMMIT + d);
    });

    tl.to(root, { opacity: 0, duration: 0.5, ease: 'power1.in' }, DURATION - 0.5);
    tl.set({}, {}, DURATION);
  }

  ctx = gsap.context(build, svg);
  if (document.fonts && document.fonts.load) {
    Promise.all(['400', '500', '600', '700'].map(w => document.fonts.load(`${w} 30px Sora`)))
      .then(() => layouts.forEach(fn => fn())).catch(() => {});
  }

  function play() { if (master) master.restart(); }
  function pause() { if (master) master.pause(); }
  function destroy() {
    if (ctx) ctx.revert();
    ctx = null; master = null;
    svg.remove();
  }
  function seek(s) { if (!master) return; master.pause(); master.seek(s); }
  if (opts.autoplay) play();
  return { play, pause, destroy, seek, duration: DURATION, aspect: ASPECT, commitAt: T_COMMIT };
}

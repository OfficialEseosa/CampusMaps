// explainer.js, "How CampusMaps works" in five chapters, built for a transparent 1920x1080 video overlay.
// Pure SVG + global gsap. Everything runs on ONE finite, paused gsap timeline (virtual-clock safe:
// no setTimeout, no Date, no CSS animation, no infinite repeats, no randomness).
// Contract: mount(el, opts) -> { play(), pause(), destroy(), seek(s), duration, chapters }
//   play()   restarts the timeline from 0
//   seek(s)  jumps to s seconds and holds (paused)
// opts.aspect: '16:9' (1920x1080, default), '3:2' (2160x1440) or '3:4' (1620x2160, portrait reflow).
// opts.chapter: 0..4 renders that chapter alone: 0.4 s fade-in, build, hold, 0.5 s fade-out, 7.5 s total,
//   no progress dots, eyebrow "HOW CAMPUSMAPS WORKS". Without it: the full run, chapter starts
//   0, 7, 14, 21, 28 s, everything fades out 34.5 -> 35.0 (fully transparent at 35).

const NS = 'http://www.w3.org/2000/svg';
const C = {
  ink: '#1b1614', clay: '#c67c4e', warm: '#ffb68c', teal: '#7ad0e0',
  gold: '#ffd166', err: '#e05a4e', white: '#ffffff', grey: '#9c9793',
};
const DIM = 'rgba(255,255,255,0.8)';
const FAINT = 'rgba(255,255,255,0.35)';
const PANEL = 'rgba(20,16,14,0.55)';

// title-safe frame
const SX = 96, SR = 1824, SAFE_W = SR - SX;
const Y_KICK = 112, Y_TITLE = 186, Y_CHIPS_BOTTOM = 832, Y_CAP = 898, CAP_LH = 46, Y_DOTS = 998;

const CH_LEN = 7, N_CH = 5, END_FADE = 34.5, DURATION = 35, SINGLE_LEN = 7.5;

// Frame layouts. k scales spacing; the 16:9 numbers reproduce the constants above exactly.
const LAYOUTS = {
  '16:9': { W: 1920, H: 1080, mx: 96, my: 54, k: 1, kickSize: 22, kickGap: 74, titleSize: 64, titleLH: 1.15, titleMaxLines: 1,
    chipSize: 24, capSize: 34, capLH: 46, maxScale: 1, identity: true },
  '3:2': { W: 2160, H: 1440, mx: 108, my: 72, k: 1.125, kickSize: 25, kickGap: 83, titleSize: 72, titleLH: 1.15, titleMaxLines: 1,
    chipSize: 27, capSize: 38, capLH: 52, maxScale: 1.35 },
  '3:4': { W: 1620, H: 2160, mx: 81, my: 108, k: 1.35, kickSize: 30, kickGap: 108, titleSize: 88, titleLH: 1.14, titleMaxLines: 2,
    chipSize: 32, capSize: 40, capLH: 54, maxScale: 1.7, portrait: true },
};
// Art regions (in the 1920x1080 authoring space) that get scaled into each chapter's free box.
const REGIONS = {
  land: [[250, 222, 1800, 745], [250, 222, 1800, 745], [96, 276, 1824, 728], [466, 232, 1512, 760], [150, 238, 1625, 705]],
  port: [[250, 222, 1432, 752], [250, 222, 1432, 752], [0, 0, 1430, 1030], [466, 232, 1512, 760], [150, 238, 1275, 1078]],
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
function fit(t, maxW) {
  const w = textLen(t, 0);
  if (w > maxW) t.setAttribute('font-size', (parseFloat(t.getAttribute('font-size')) * maxW / w).toFixed(1));
}
const lerp = (a, b, u) => a + (b - a) * u;
const clamp01 = (u) => Math.max(0, Math.min(1, u));
function quad(p0, c, p1, u) {
  const a = (1 - u) * (1 - u), b = 2 * (1 - u) * u, d = u * u;
  return [a * p0[0] + b * c[0] + d * p1[0], a * p0[1] + b * c[1] + d * p1[1]];
}

// ---------------------------------------------------------------- graph data (chapters 1 and 2)
// Plan coordinates in decimetres (so 184 = 18.4 m); projected obliquely onto the stage.
const GO = { x: 300, y: 300 };
const P = (x, y) => [GO.x + x + 0.45 * y, GO.y + 0.75 * y];
const NODES = [
  { id: 'E-W', x: 0, y: 200, k: 'ent', lab: 'E-W', la: 'left' },
  { id: 'H1', x: 150, y: 200, k: 'hall' },
  { id: 'H2', x: 450, y: 200, k: 'hall' },
  { id: 'H3', x: 700, y: 200, k: 'hall' },
  { id: 'R-608', x: 884, y: 200, k: 'room', lab: 'R-608', la: 'right' },
  { id: 'N1', x: 150, y: 60, k: 'hall' },
  { id: 'EL-6', x: 300, y: 60, k: 'vert', lab: 'EL-6', la: 'up' },
  { id: 'N2', x: 700, y: 60, k: 'hall' },
  { id: 'ST-2', x: 840, y: 60, k: 'vert', lab: 'ST-2', la: 'up' },
  { id: 'E-S', x: 450, y: 380, k: 'ent', lab: 'E-S', la: 'down' },
  { id: 'R-604', x: 150, y: 330, k: 'room', lab: 'R-604', la: 'down' },
  { id: 'R-612', x: 700, y: 330, k: 'room', lab: 'R-612', la: 'down' },
];
const EDGES = [
  ['E-W', 'H1'], ['H1', 'H2'], ['H2', 'H3'], ['H3', 'R-608'], ['H1', 'N1'], ['N1', 'EL-6'],
  ['EL-6', 'N2'], ['N2', 'H3'], ['N2', 'ST-2'], ['H2', 'E-S'], ['H1', 'R-604'], ['H3', 'R-612'],
];
const LOCKED = 'H2|H3';
const SRC = 'E-W', DST = 'R-608';

function dijkstra(blocked) {
  const byId = {}; NODES.forEach(n => { byId[n.id] = n; });
  const adj = {}; NODES.forEach(n => { adj[n.id] = []; });
  for (const [a, b] of EDGES) {
    if (blocked && (`${a}|${b}` === blocked || `${b}|${a}` === blocked)) continue;
    const w = Math.hypot(byId[a].x - byId[b].x, byId[a].y - byId[b].y);
    adj[a].push([b, w]); adj[b].push([a, w]);
  }
  const dist = {}, prev = {}, done = {}, order = [];
  NODES.forEach(n => { dist[n.id] = Infinity; });
  dist[SRC] = 0;
  for (;;) {
    let u = null;
    for (const n of NODES) if (!done[n.id] && dist[n.id] < Infinity && (u === null || dist[n.id] < dist[u])) u = n.id;
    if (u === null) break;
    done[u] = true; order.push(u);
    for (const [v, w] of adj[u]) if (dist[u] + w < dist[v]) { dist[v] = dist[u] + w; prev[v] = u; }
  }
  const path = [];
  for (let v = DST; v; v = prev[v]) path.unshift(v);
  return { dist, order, path, len: dist[DST] };
}

export function mount(el, opts = {}) {
  const ASPECT = LAYOUTS[opts.aspect] ? opts.aspect : '16:9';
  const LY = LAYOUTS[ASPECT];
  const PORT = !!LY.portrait;
  const chOpt = opts.chapter === undefined || opts.chapter === null ? NaN : Number(opts.chapter);
  const SINGLE = Number.isInteger(chOpt) && chOpt >= 0 && chOpt < N_CH ? chOpt : null;
  const LX = LY.mx, LR = LY.W - LY.mx, LW = LR - LX;
  const GF = PORT ? 1.35 : 1;   // graph label boost: the portrait graph is scaled less, so its labels need more size
  const svg = E('svg', { viewBox: `0 0 ${LY.W} ${LY.H}`, width: '100%', height: '100%',
    preserveAspectRatio: 'xMidYMid meet', role: 'img',
    'aria-label': 'How CampusMaps works: building graph, time-aware routing, localisation, AR arrows, glasses and watch' });
  svg.style.display = 'block';
  svg.style.overflow = 'visible';
  el.appendChild(svg);

  // ---- defs ----
  const defs = E('defs', null, svg);
  const sh = E('filter', { id: 'ex-shadow', filterUnits: 'userSpaceOnUse', x: -100, y: -100, width: LY.W + 200, height: LY.H + 200 }, defs);
  E('feDropShadow', { dx: 0, dy: 2, stdDeviation: 4, 'flood-color': '#000', 'flood-opacity': 0.45 }, sh);
  const glow = E('filter', { id: 'ex-glow', x: '-50%', y: '-50%', width: '200%', height: '200%' }, defs);
  E('feGaussianBlur', { stdDeviation: 6, result: 'b' }, glow);
  const fm = E('feMerge', null, glow);
  E('feMergeNode', { in: 'b' }, fm);
  E('feMergeNode', { in: 'SourceGraphic' }, fm);
  const gChev = E('linearGradient', { id: 'ex-chev', x1: 0, y1: 1, x2: 0, y2: 0 }, defs);
  E('stop', { offset: '0', 'stop-color': C.clay }, gChev);
  E('stop', { offset: '1', 'stop-color': C.warm }, gChev);
  const gFloor = E('linearGradient', { id: 'ex-floor', x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
  E('stop', { offset: '0', 'stop-color': C.teal, 'stop-opacity': 0.03 }, gFloor);
  E('stop', { offset: '1', 'stop-color': C.teal, 'stop-opacity': 0.2 }, gFloor);
  const gPhone = E('linearGradient', { id: 'ex-phone', x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
  E('stop', { offset: '0', 'stop-color': '#2a211d', 'stop-opacity': 0.92 }, gPhone);
  E('stop', { offset: '1', 'stop-color': '#16110f', 'stop-opacity': 0.92 }, gPhone);

  const root = E('g', { filter: 'url(#ex-shadow)' }, svg);
  const Lgraph = E('g', null, root);
  const Lchap = E('g', null, root);
  const Ldots = E('g', null, root);

  // ---- helpers that register initial (hidden) states; applied inside the gsap context ----
  // Text measurement depends on Sora being loaded; every layout step is re-run once the faces are in.
  const layouts = [];
  const L = (fn) => { fn(); layouts.push(fn); };
  const fitL = (t, maxW) => { const base = t.getAttribute('font-size'); L(() => { t.setAttribute('font-size', base); fit(t, maxW); }); };

  const hidden = [];     // [el, vars] set at build time
  const hide = (elx, vars) => { hidden.push([elx, vars || { opacity: 0 }]); return elx; };

  // hidden text used to measure strings for wrapping
  const meas = E('text', { x: 0, y: -1000, opacity: 0, 'font-family': 'Sora, sans-serif' }, svg);
  function measure(str, size, weight) {
    meas.setAttribute('font-size', size); meas.setAttribute('font-weight', weight); meas.textContent = str;
    return textLen(meas, str.length * size * 0.58);
  }
  // Keep the authored line breaks when they fit; otherwise wrap greedily; shrink if it needs more than maxLines.
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

  function chipRow(parent, list) {
    const c = LY.chipSize / 24;
    return list.map(s => {
      const outer = E('g', null, parent);
      const g = E('g', null, outer);
      const r = E('rect', { x: 0, y: 0, height: 48 * c, rx: 24 * c, fill: PANEL, stroke: 'rgba(255,182,140,0.55)', 'stroke-width': 1.8 * c }, g);
      E('circle', { cx: 24 * c, cy: 24 * c, r: 5 * c, fill: C.clay }, g);
      const t = T(g, 40 * c, 33 * c, s, { 'font-size': LY.chipSize, 'font-weight': 500 });
      hide(g, { opacity: 0, y: 14 * LY.k });
      return { g, outer, r, t, s, w: 0 };
    });
  }

  function chapter(i, title, capLines, chips) {
    const g = hide(E('g', null, Lchap));
    const kick = T(g, LX, 0, SINGLE === null ? `${i + 1} / 5 · HOW CAMPUSMAPS WORKS` : 'HOW CAMPUSMAPS WORKS',
      { 'font-size': LY.kickSize, 'font-weight': 600, fill: C.warm, 'letter-spacing': 3 * LY.k });
    const ttl = T(g, LX, 0, '', { 'font-size': LY.titleSize, 'font-weight': 700 });
    hide(kick, { opacity: 0, y: 20 * LY.k }); hide(ttl, { opacity: 0, y: 44 * LY.k });
    const artWrap = E('g', null, g);
    const art = E('g', null, artWrap);
    const cg = E('g', null, g);
    const chipItems = chips ? chipRow(cg, chips) : [];
    const cap = hide(E('g', null, g), { opacity: 0, y: 16 * LY.k });
    const capT = T(cap, LX, 0, '', { 'font-size': LY.capSize, 'font-weight': 500, fill: 'rgba(255,255,255,0.94)' });
    return { i, g, kick, ttl, title, art, artWrap, chipItems, chips: chipItems.map(c => c.g), cap, capT, capLines, box: null };
  }

  // Places eyebrow, title, chips and caption for one chapter and records the free box for its art.
  function layoutChapter(ch) {
    const k = LY.k;
    const kickY = LY.my + 58 * k, titleY = kickY + LY.kickGap;
    ch.kick.setAttribute('y', kickY);
    const tw = wrapLines([ch.title], LY.titleSize, 700, LW, LY.titleMaxLines);
    ch.ttl.setAttribute('font-size', tw.size.toFixed(1));
    const tlh = tw.size * LY.titleLH;
    setLines(ch.ttl, tw.lines, LX, titleY, tlh);
    const titleBottom = titleY + (tw.lines.length - 1) * tlh + tw.size * 0.28;

    const dotsY = LY.H - LY.my - 28 * k;
    const capLast = SINGLE === null ? dotsY - 54 * k : LY.H - LY.my - 12 * k;
    const cw = wrapLines(ch.capLines, LY.capSize, 500, LW, 4);
    ch.capT.setAttribute('font-size', cw.size.toFixed(1));
    const capFirst = capLast - (cw.lines.length - 1) * LY.capLH;
    setLines(ch.capT, cw.lines, LX, capFirst, LY.capLH);

    const c = LY.chipSize / 24, chH = 48 * c, rowStep = 60 * c, gap = 14 * c;
    let chipsTop = capFirst - LY.capSize * 1.4;
    if (ch.chipItems.length) {
      for (const it of ch.chipItems) { it.w = textLen(it.t, it.s.length * LY.chipSize * 0.6) + 62 * c; it.r.setAttribute('width', it.w); }
      const rows = [[]]; let x = 0;
      for (const it of ch.chipItems) {
        if (x > 0 && x + it.w > LW) { rows.push([]); x = 0; }
        rows[rows.length - 1].push(it); x += it.w + gap;
      }
      const chipsBottom = capFirst - 66 * k;
      rows.forEach((row, ri) => {
        const y = chipsBottom - chH - (rows.length - 1 - ri) * rowStep;
        let cx = LX;
        for (const it of row) { it.outer.setAttribute('transform', `translate(${cx.toFixed(1)},${y.toFixed(1)})`); cx += it.w + gap; }
      });
      chipsTop = chipsBottom - chH - (rows.length - 1) * rowStep;
    }
    ch.box = [LX, titleBottom + 36 * k, LR, chipsTop - 36 * k];
  }
  function fitRegion(reg, box) {
    const rw = reg[2] - reg[0], rh = reg[3] - reg[1], bw = box[2] - box[0], bh = box[3] - box[1];
    const sc = Math.min(bw / rw, bh / rh, LY.maxScale);
    const tx = box[0] + (bw - rw * sc) / 2 - reg[0] * sc, ty = box[1] + (bh - rh * sc) / 2 - reg[1] * sc;
    return `translate(${tx.toFixed(2)},${ty.toFixed(2)}) scale(${sc.toFixed(4)})`;
  }
  function layoutFrame() {
    CH.forEach(layoutChapter);
    if (LY.identity) return;
    const regs = PORT ? REGIONS.port : REGIONS.land;
    // chapters 1 and 2 share the graph layer, so they share one box and one transform
    const b0 = CH[0].box, b1 = CH[1].box;
    const shared = [b0[0], Math.max(b0[1], b1[1]), b0[2], Math.min(b0[3], b1[3])];
    CH.forEach((ch, i) => {
      const tf = fitRegion(regs[i], i < 2 ? shared : ch.box);
      ch.artWrap.setAttribute('transform', tf);
      if (i === 0) Lgraph.setAttribute('transform', tf);
    });
  }

  // ---- progress dots ----
  const dotOn = [];
  for (let i = 0; i < N_CH; i++) {
    const cx = LX + (10 + i * 34) * LY.k, cy = LY.H - LY.my - 28 * LY.k;
    E('circle', { cx, cy, r: 7 * LY.k, fill: C.white, opacity: 0.4 }, Ldots);
    dotOn.push(hide(E('circle', { cx, cy, r: 10 * LY.k, fill: C.clay, stroke: C.warm, 'stroke-width': 1.5 }, Ldots), { opacity: 0, scale: 0.4, transformOrigin: '50% 50%' }));
  }
  if (SINGLE !== null) Ldots.setAttribute('display', 'none');
  hide(Ldots, { opacity: 0 });

  // ================================================================ GRAPH (shared by 1 and 2)
  const byId = {}; NODES.forEach(n => { byId[n.id] = n; n.p = P(n.x, n.y); });
  const gSlab = hide(E('g', null, Lgraph));
  {
    const poly = (pts, attrs) => E('path', Object.assign({ d: 'M' + pts.map(q => P(q[0], q[1]).join(',')).join(' L') + ' Z' }, attrs), gSlab);
    poly([[-30, -20], [930, -20], [930, 420], [-30, 420]], { fill: 'rgba(255,255,255,0.045)', stroke: 'rgba(255,255,255,0.4)', 'stroke-width': 2 });
    const rooms = [[-30, -20, 132, 182], [168, 78, 432, 182], [468, 78, 682, 182], [718, 78, 930, 182], [168, -20, 930, 42],
      [-30, 218, 132, 420], [168, 218, 432, 420], [468, 218, 682, 420], [718, 218, 930, 420]];
    for (const [x0, y0, x1, y1] of rooms) {
      poly([[x0 + 6, y0 + 6], [x1 - 6, y0 + 6], [x1 - 6, y1 - 6], [x0 + 6, y1 - 6]],
        { fill: 'none', stroke: 'rgba(255,255,255,0.16)', 'stroke-width': 1.5 });
    }
  }
  const gEdges = E('g', null, Lgraph);
  const gPath = E('g', null, Lgraph);
  const gNodes = E('g', null, Lgraph);
  const gNodeLab = hide(E('g', null, Lgraph));

  const firstRun = dijkstra(null);
  const reRun = dijkstra(LOCKED);
  const maxD = Math.max(...firstRun.order.map(id => firstRun.dist[id]));

  const edgeEls = EDGES.map(([a, b]) => {
    const A = byId[a].p, B = byId[b].p;
    const len = Math.hypot(B[0] - A[0], B[1] - A[1]);
    const ln = E('line', { x1: A[0], y1: A[1], x2: B[0], y2: B[1], stroke: 'rgba(255,255,255,0.62)', 'stroke-width': 4, 'stroke-linecap': 'round' }, gEdges);
    ln.style.strokeDasharray = `${len} ${len}`;
    hide(ln, { strokeDashoffset: len });
    const d0 = Math.min(firstRun.dist[a], firstRun.dist[b]);
    return { a, b, ln, len, d0 };
  });

  // door on H2-H3 (turns red + locked in chapter 2)
  const doorA = P(575, 176), doorB = P(575, 224);
  const doorMid = P(575, 200);
  const door = hide(E('line', { x1: doorA[0], y1: doorA[1], x2: doorB[0], y2: doorB[1], stroke: C.white, 'stroke-width': 7, 'stroke-linecap': 'round' }, gNodes));

  const nodeEls = {};
  for (const n of NODES) {
    const [x, y] = n.p;
    let s;
    if (n.k === 'room') s = E('rect', { x: x - 13, y: y - 13, width: 26, height: 26, rx: 6, fill: C.warm, stroke: C.ink, 'stroke-width': 2 }, gNodes);
    else if (n.k === 'ent') s = E('circle', { cx: x, cy: y, r: 14, fill: C.white, stroke: C.ink, 'stroke-width': 2 }, gNodes);
    else if (n.k === 'vert') s = E('circle', { cx: x, cy: y, r: 14, fill: C.teal, stroke: C.ink, 'stroke-width': 2 }, gNodes);
    else s = E('circle', { cx: x, cy: y, r: 11, fill: C.grey, stroke: C.ink, 'stroke-width': 2 }, gNodes);
    hide(s, { opacity: 0, scale: 0.2, transformOrigin: '50% 50%' });
    nodeEls[n.id] = s;
    if (n.lab) {
      const o = { left: [-26, 8 * GF, 'end'], right: [26, 8 * GF, 'start'], up: [0, -28 * GF, 'middle'], down: [0, 22 + 20 * GF, 'middle'] }[n.la];
      T(gNodeLab, x + o[0], y + o[1], n.lab, { 'font-size': 22 * GF, 'font-weight': 600, fill: DIM, 'text-anchor': o[2] });
    }
  }

  // ================================================================ CHAPTER 1: graph
  const CH = [];
  CH[0] = chapter(0, 'The building is a graph.',
    ['We walk every hallway once. Every door, elevator and room', 'becomes a node with real metres.'],
    ['surveyed on foot', 'JSON per building', 'validator']);
  const c1 = {};
  {
    const a = CH[0].art;
    // "node" callout -> N1
    const n1 = byId.N1.p;
    c1.nodeLine = E('line', { x1: n1[0], y1: 272, x2: n1[0], y2: n1[1] - 18, stroke: C.warm, 'stroke-width': 2.5, 'stroke-linecap': 'round' }, a);
    c1.nodeLine.style.strokeDasharray = '60 60';
    hide(c1.nodeLine, { strokeDashoffset: 60 });
    c1.nodeTx = hide(T(a, n1[0], 260, 'node', { 'font-size': 28 * GF, 'font-weight': 700, fill: C.warm, 'text-anchor': 'middle' }), { opacity: 0, y: 10 });
    // "edge · 18.4 m" on H3 -> R-608
    const A = byId.H3.p, B = byId['R-608'].p;
    c1.edgeHi = E('line', { x1: A[0] + 16, y1: A[1], x2: B[0] - 16, y2: B[1], stroke: C.warm, 'stroke-width': 7, 'stroke-linecap': 'round' }, a);
    const el = B[0] - A[0] - 32;
    c1.edgeHi.style.strokeDasharray = `${el} ${el}`;
    hide(c1.edgeHi, { strokeDashoffset: el });
    c1.edgeTx = hide(T(a, (A[0] + B[0]) / 2 + (GF - 1) * 60, A[1] - 22, 'edge · 18.4 m', { 'font-size': 26 * GF, 'font-weight': 700, fill: C.warm, 'text-anchor': 'middle' }), { opacity: 0, y: 10 });
    // legend
    const lgOuter = E('g', null, a);
    const lg = hide(E('g', null, lgOuter), { opacity: 0, y: 12 });
    const items = [['ent', 'entrance'], ['hall', 'hallway'], ['vert', 'elevator / stairs'], ['room', 'room']];
    const li = items.map(([k, s]) => {
      const g = E('g', null, lg);
      if (k === 'room') E('rect', { x: 0, y: -11, width: 22, height: 22, rx: 5, fill: C.warm }, g);
      else E('circle', { cx: 11, cy: 0, r: k === 'hall' ? 9 : 11, fill: k === 'ent' ? C.white : k === 'vert' ? C.teal : C.grey }, g);
      return { g, s, t: T(g, 32, 9 * GF, s, { 'font-size': 24 * GF, fill: DIM }) };
    });
    L(() => {
      let x = 0;
      for (const it of li) { it.g.setAttribute('transform', `translate(${x},0)`); x += 32 + textLen(it.t, it.s.length * 14) + 44; }
      lgOuter.setAttribute('transform', `translate(${Math.round(840 - (x - 44) / 2)},684)`);
    });
    c1.legend = lg;
  }

  // ================================================================ CHAPTER 2: Dijkstra with time
  CH[1] = chapter(1, 'Dijkstra picks the route, with time in it.',
    ['The router weighs every entrance, stairs versus elevator,', 'and which doors need a card right now.'],
    ['pure Kotlin core', 'entrance × stairs/elevator', 'PantherCard hours', 'now is a parameter', '45 tests']);
  const c2 = { rings: {}, lit: {} };
  {
    const a = CH[1].art;
    for (const n of NODES) {
      const [x, y] = n.p;
      c2.lit[n.id] = hide(E('circle', { cx: x, cy: y, r: 22, fill: 'none', stroke: C.teal, 'stroke-width': 3 }, a));
      c2.rings[n.id] = hide(E('circle', { cx: x, cy: y, r: 16, fill: 'none', stroke: C.teal, 'stroke-width': 3 }, a));
    }
    const mkPath = (ids, color, width) => {
      let d = '', len = 0;
      ids.forEach((id, i) => {
        const p = byId[id].p;
        d += (i ? ' L' : 'M') + p[0] + ',' + p[1];
        if (i) { const q = byId[ids[i - 1]].p; len += Math.hypot(p[0] - q[0], p[1] - q[1]); }
      });
      const pe = E('path', { d, fill: 'none', stroke: color, 'stroke-width': width, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', filter: 'url(#ex-glow)' }, gPath);
      pe.style.strokeDasharray = `${len} ${len}`;
      return { pe, len };
    };
    c2.p1 = mkPath(firstRun.path, C.clay, 10);
    hide(c2.p1.pe, { strokeDashoffset: c2.p1.len });
    c2.p1red = mkPath(firstRun.path, C.err, 10);
    c2.p1red.pe.style.strokeDashoffset = '0';
    hide(c2.p1red.pe);
    c2.p2 = mkPath(reRun.path, C.clay, 10);
    hide(c2.p2.pe, { strokeDashoffset: c2.p2.len });

    // red door overlay + lock + label
    c2.doorRed = hide(E('line', { x1: doorA[0], y1: doorA[1], x2: doorB[0], y2: doorB[1], stroke: C.err, 'stroke-width': 9, 'stroke-linecap': 'round' }, a));
    const lk = E('g', { transform: `translate(${doorMid[0]},${doorMid[1] - 50})` }, a);
    c2.lock = hide(E('g', null, lk), { opacity: 0, scale: 0.3, transformOrigin: '50% 50%' });
    E('path', { d: 'M-9,-4 L-9,-12 A9,9 0 0 1 9,-12 L9,-4', fill: 'none', stroke: C.err, 'stroke-width': 4, 'stroke-linecap': 'round' }, c2.lock);
    E('rect', { x: -15, y: -5, width: 30, height: 24, rx: 5, fill: C.err }, c2.lock);
    E('circle', { cx: 0, cy: 6, r: 3.5, fill: C.ink }, c2.lock);
    c2.lockTx = hide(T(a, doorMid[0], doorMid[1] + 50, 'card only', { 'font-size': 24 * GF, 'font-weight': 700, fill: C.err, 'text-anchor': 'middle' }), { opacity: 0, y: 8 });

    // clock chip (Fri 14:00 -> Sat 21:00)
    // landscape: clock + readout right of the graph; portrait: in a row under it
    const cx0 = PORT ? 300 : 1486, cy0 = PORT ? 660 : 300;
    const rx0 = PORT ? 640 : cx0 + 6, ry0 = PORT ? 686 : 420;
    c2.clock = hide(E('g', null, E('g', { transform: `translate(${cx0},${cy0})` }, a)), { opacity: 0, y: 12 });
    E('rect', { x: 0, y: 0, width: 300, height: 68, rx: 34, fill: PANEL, stroke: C.gold, 'stroke-width': 2.2 }, c2.clock);
    E('circle', { cx: 38, cy: 34, r: 16, fill: 'none', stroke: C.gold, 'stroke-width': 3 }, c2.clock);
    E('path', { d: 'M38,24 L38,34 L46,39', fill: 'none', stroke: C.gold, 'stroke-width': 3, 'stroke-linecap': 'round' }, c2.clock);
    c2.t1 = T(c2.clock, 72, 45, 'Fri 14:00', { 'font-size': 32, 'font-weight': 700 });
    c2.t2 = T(c2.clock, 72, 45, 'Sat 21:00', { 'font-size': 32, 'font-weight': 700, fill: C.gold });
    hide(c2.t2, { scaleY: 0, transformOrigin: '50% 50%' });
    // readout
    c2.read = hide(E('g', null, E('g', { transform: `translate(${rx0},${ry0})` }, a)), { opacity: 0, y: 12 });
    c2.rl1 = T(c2.read, 0, 0, 'shortest route', { 'font-size': 26, fill: DIM });
    c2.rv1 = T(c2.read, 0, 56, (firstRun.len / 10).toFixed(1) + ' m', { 'font-size': 48, 'font-weight': 700, fill: C.warm });
    c2.rl2 = hide(T(c2.read, 0, 0, 're-routed', { 'font-size': 26, 'font-weight': 600, fill: C.err }));
    c2.rv2 = hide(T(c2.read, 0, 56, (reRun.len / 10).toFixed(1) + ' m', { 'font-size': 48, 'font-weight': 700, fill: C.warm }));
  }

  // ================================================================ CHAPTER 3: localisation strip
  CH[2] = chapter(2, 'Finding you: outdoors → door → room.',
    ['Satellites get you to the door.', 'Signs and the barometer get you to the room.'], null);
  const c3 = { cards: [] };
  {
    const a = CH[2].art;
    const stages = [
      { icon: 'sat', head: ['GPS'], sub: ['outdoors'] },
      { icon: 'globe', head: ['ARCore', 'Geospatial API'], sub: ['VPS ~1 m'] },
      { icon: 'door', head: ['Door heading'], sub: ['+ compass'] },
      { icon: 'sign', head: ['ML Kit OCR'], sub: ['ARCore', 'Augmented Images'] },
      { icon: 'baro', head: ['Barometer'], sub: ['→ floor'] },
    ];
    if (PORT) {
      // portrait: a vertical column of wide cards, brackets on the right
      const cw = 1150, chh = 180, gap = 32, x = 0;
      stages.forEach((st, i) => {
        const y = i * (chh + gap), my = y + chh / 2;
        const g = hide(E('g', null, a), { opacity: 0, x: -24 });
        E('rect', { x, y, width: cw, height: chh, rx: 26, fill: PANEL, stroke: 'rgba(255,255,255,0.28)', 'stroke-width': 2 }, g);
        const lit = hide(E('rect', { x, y, width: cw, height: chh, rx: 26, fill: 'rgba(198,124,78,0.16)', stroke: C.clay, 'stroke-width': 3.5 }, g));
        E('circle', { cx: x + 42, cy: y + 42, r: 22, fill: 'none', stroke: 'rgba(255,255,255,0.5)', 'stroke-width': 2 }, g);
        const numOn = hide(E('circle', { cx: x + 42, cy: y + 42, r: 22, fill: C.clay }, g));
        T(g, x + 42, y + 51, String(i + 1), { 'font-size': 24, 'font-weight': 700, 'text-anchor': 'middle' });
        const ic = E('g', { transform: `translate(${x + 180},${my}) scale(1.25)` }, g);
        const icon = hide(E('g', null, ic), { opacity: 0.4 });
        drawIcon(icon, st.icon);
        fitL(T(g, x + 310, my - 6, st.head.join(' '), { 'font-size': 46, 'font-weight': 700 }), cw - 340);
        fitL(T(g, x + 310, my + 46, st.sub.join(' '), { 'font-size': 34, 'font-weight': 500, fill: C.warm }), cw - 340);
        c3.cards.push({ g, lit, numOn, icon, pos: my });
        if (i < stages.length - 1) {
          const ay = y + chh + gap / 2;
          c3.cards[i].arrow = hide(E('path', { d: `M${x + 166},${ay - 7} L${x + 180},${ay + 7} L${x + 194},${ay - 7}`,
            fill: 'none', stroke: C.warm, 'stroke-width': 4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, a));
        }
      });
      const bx = cw + 36;
      const span = (i0, i1) => [i0 * (chh + gap) + 12, i1 * (chh + gap) + chh - 12];
      const groups = [[0, 1, 'outdoors', C.gold], [2, 2, 'door', C.warm], [3, 4, 'room', C.clay]];
      c3.brackets = groups.map(([i0, i1, s, col]) => {
        const [y0, y1] = span(i0, i1);
        const g = hide(E('g', null, a), { opacity: 0, x: 10 });
        E('path', { d: `M${bx - 16},${y0} L${bx},${y0} L${bx},${y1} L${bx - 16},${y1}`, fill: 'none', stroke: col, 'stroke-width': 3.5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, g);
        T(g, bx + 26, (y0 + y1) / 2 + 14, s, { 'font-size': 40, 'font-weight': 700, fill: col });
        return g;
      });
      c3.you = hide(E('circle', { cx: bx, cy: 0, r: 13, fill: C.white, stroke: C.clay, 'stroke-width': 4, filter: 'url(#ex-glow)' }, a), { opacity: 0, y: c3.cards[0].pos });
      c3.axis = 'y';
    } else {
      const cw = 312, gap = 42, cy = 280, chh = 340;
      stages.forEach((st, i) => {
        const x = SX + i * (cw + gap), mx = x + cw / 2;
        const g = hide(E('g', null, a), { opacity: 0, y: 24 });
        E('rect', { x, y: cy, width: cw, height: chh, rx: 24, fill: PANEL, stroke: 'rgba(255,255,255,0.28)', 'stroke-width': 2 }, g);
        const lit = hide(E('rect', { x, y: cy, width: cw, height: chh, rx: 24, fill: 'rgba(198,124,78,0.16)', stroke: C.clay, 'stroke-width': 3.5 }, g));
        E('circle', { cx: x + 36, cy: cy + 36, r: 18, fill: 'none', stroke: 'rgba(255,255,255,0.5)', 'stroke-width': 2 }, g);
        const numOn = hide(E('circle', { cx: x + 36, cy: cy + 36, r: 18, fill: C.clay }, g));
        T(g, x + 36, cy + 43, String(i + 1), { 'font-size': 20, 'font-weight': 700, 'text-anchor': 'middle' });
        const ic = E('g', { transform: `translate(${mx},${cy + 104})` }, g);
        const icon = hide(E('g', null, ic), { opacity: 0.4 });
        drawIcon(icon, st.icon);
        let ty = cy + 206;
        st.head.forEach(s => { const t = T(g, mx, ty, s, { 'font-size': 30, 'font-weight': 700, 'text-anchor': 'middle' }); fitL(t, cw - 28); ty += 36; });
        ty += 2;
        st.sub.forEach(s => { const t = T(g, mx, ty, s, { 'font-size': 24, 'font-weight': 500, fill: C.warm, 'text-anchor': 'middle' }); fitL(t, cw - 28); ty += 30; });
        c3.cards.push({ g, lit, numOn, icon, pos: mx });
        if (i < stages.length - 1) {
          const ax = x + cw + gap / 2;
          const arr = hide(E('path', { d: `M${ax - 7},${cy + chh / 2 - 14} L${ax + 7},${cy + chh / 2} L${ax - 7},${cy + chh / 2 + 14}`,
            fill: 'none', stroke: C.warm, 'stroke-width': 4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, a));
          c3.cards[i].arrow = arr;
        }
      });
      // brackets: outdoors (1-2), door (3), room (4-5)
      const by = 672;
      const span = (i0, i1) => [SX + i0 * (cw + gap) + 10, SX + i1 * (cw + gap) + cw - 10];
      const groups = [[0, 1, 'outdoors', C.gold], [2, 2, 'door', C.warm], [3, 4, 'room', C.clay]];
      c3.brackets = groups.map(([i0, i1, s, col]) => {
        const [x0, x1] = span(i0, i1);
        const g = hide(E('g', null, a), { opacity: 0, y: 10 });
        E('path', { d: `M${x0},${by - 14} L${x0},${by} L${x1},${by} L${x1},${by - 14}`, fill: 'none', stroke: col, 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, g);
        T(g, (x0 + x1) / 2, by + 42, s, { 'font-size': 30, 'font-weight': 700, fill: col, 'text-anchor': 'middle' });
        return g;
      });
      c3.you = hide(E('circle', { cx: 0, cy: by, r: 11, fill: C.white, stroke: C.clay, 'stroke-width': 4, filter: 'url(#ex-glow)' }, a), { opacity: 0, x: c3.cards[0].pos });
      c3.axis = 'x';
    }
  }

  // ================================================================ CHAPTER 4: AR arrows
  CH[3] = chapter(3, 'Arrows on the floor.',
    ['World-locked, metre-wide arrows through the phone camera.', 'Everything on-device, no network.'],
    ['ARCore 1.56', 'SceneView 4.38', 'Jetpack Compose', 'CameraX']);
  const c4 = { chevs: [] };
  const HZ = 430, NEARY = 700, VPX = 630;
  {
    const a = CH[3].art;
    const px = 470, py = 236, pw = 320, ph = 520;
    const sx0 = px + 14, sy0 = py + 14, sw = pw - 28, shh = ph - 28;
    c4.phone = hide(E('g', null, a), { opacity: 0, scale: 0.92, transformOrigin: '50% 50%' });
    E('rect', { x: px, y: py, width: pw, height: ph, rx: 46, fill: 'url(#ex-phone)', stroke: C.clay, 'stroke-width': 4 }, c4.phone);
    const clip = E('clipPath', { id: 'ex-screen' }, defs);
    E('rect', { x: sx0, y: sy0, width: sw, height: shh, rx: 34 }, clip);
    const scr = E('g', { 'clip-path': 'url(#ex-screen)' }, c4.phone);
    E('rect', { x: sx0, y: sy0, width: sw, height: shh, fill: '#1e1815' }, scr);
    // corridor in perspective
    const tl_ = 585, tr_ = 675, ceil = 356;
    const walls = hide(E('g', { fill: 'none', stroke: 'rgba(255,255,255,0.3)', 'stroke-width': 2 }, scr));
    E('path', { d: `M${sx0},${sy0} L${tl_},${ceil} L${tl_},${HZ} L${sx0},${sy0 + shh}` }, walls);
    E('path', { d: `M${sx0 + sw},${sy0} L${tr_},${ceil} L${tr_},${HZ - 24}` }, walls);
    E('path', { d: `M${tl_},${ceil} L${tr_},${ceil}` }, walls);
    E('path', { d: `M${tr_},${HZ} L${sx0 + sw},${sy0 + shh}` }, walls);
    E('path', { d: `M${tr_},${HZ - 24} L${tr_ + 24},${HZ - 30}`, stroke: 'rgba(255,209,102,0.5)' }, walls);  // side opening
    E('path', { d: `M${tl_},${HZ} L${tr_},${HZ} L${sx0 + sw},${sy0 + shh} L${sx0},${sy0 + shh} Z`, fill: 'url(#ex-floor)', stroke: 'none' }, walls);
    const grid = hide(E('g', { stroke: 'rgba(122,208,224,0.35)', 'stroke-width': 1.4 }, scr));
    for (let i = 0; i <= 6; i++) {
      const f = i / 6;
      E('line', { x1: lerp(tl_, tr_, f), y1: HZ, x2: lerp(sx0, sx0 + sw, f), y2: sy0 + shh }, grid);
    }
    for (const z of [1, 1.4, 2, 3, 5]) {
      const yy = HZ + (NEARY - HZ) / z, f = (yy - HZ) / (sy0 + shh - HZ);
      E('line', { x1: lerp(tl_, sx0, f), y1: yy, x2: lerp(tr_, sx0 + sw, f), y2: yy }, grid);
    }
    c4.grid = grid; c4.walls = walls;
    // turn arrow (gold) at the far end
    c4.turn = hide(E('path', { d: `M${VPX},${HZ + 64} L${VPX},${HZ + 30} Q${VPX},${HZ + 16} ${VPX + 16},${HZ + 16} L${VPX + 44},${HZ + 16} ` +
      `M${VPX + 34},${HZ + 5} L${VPX + 48},${HZ + 16} L${VPX + 34},${HZ + 27}`, fill: 'none', stroke: C.gold, 'stroke-width': 8,
      'stroke-linecap': 'round', 'stroke-linejoin': 'round', filter: 'url(#ex-glow)' }, scr), { opacity: 0, scale: 0.4, transformOrigin: '50% 100%' });
    // chevrons
    const w = 200, h = 72, th = 34, k = th * (w / 2) / h;
    const d = `M${-w / 2},${h / 2} L0,${-h / 2} L${w / 2},${h / 2} L${w / 2 - k * 1.6},${h / 2} L0,${-h / 2 + th} L${-w / 2 + k * 1.6},${h / 2} Z`;
    for (let i = 0; i < 3; i++) c4.chevs.push(E('path', { d, fill: 'url(#ex-chev)', filter: 'url(#ex-glow)', opacity: 0 }, scr));
    // banner + bottom card
    c4.banner = hide(E('g', null, scr), { opacity: 0, y: -12 });
    E('rect', { x: sx0 + 14, y: sy0 + 18, width: sw - 28, height: 54, rx: 18, fill: 'rgba(20,16,14,0.88)', stroke: 'rgba(255,209,102,0.6)', 'stroke-width': 1.5 }, c4.banner);
    E('path', { d: `M${sx0 + 36},${sy0 + 60} L${sx0 + 36},${sy0 + 42} Q${sx0 + 36},${sy0 + 34} ${sx0 + 44},${sy0 + 34} L${sx0 + 56},${sy0 + 34} M${sx0 + 50},${sy0 + 28} L${sx0 + 57},${sy0 + 34} L${sx0 + 50},${sy0 + 40}`,
      fill: 'none', stroke: C.gold, 'stroke-width': 3.5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, c4.banner);
    const bt = T(c4.banner, sx0 + 70, sy0 + 53, 'Turn right · 6 m', { 'font-size': 21, 'font-weight': 700 });
    fitL(bt, sw - 96);
    c4.card = hide(E('g', null, scr), { opacity: 0, y: 12 });
    E('rect', { x: sx0 + 14, y: sy0 + shh - 70, width: sw - 28, height: 52, rx: 18, fill: C.clay }, c4.card);
    T(c4.card, sx0 + sw / 2, sy0 + shh - 36, 'Room 3361 · 1:27', { 'font-size': 20, 'font-weight': 700, fill: C.ink, 'text-anchor': 'middle' });
    E('rect', { x: px + pw / 2 - 26, y: py + 5, width: 52, height: 6, rx: 3, fill: 'rgba(255,255,255,0.3)' }, c4.phone);

    // callouts on the right
    const rows = [
      ['pin', 'World-locked', 'pinned to the floor, not the screen'],
      ['ruler', 'A metre wide', 'readable at walking pace'],
      ['cloud', 'On-device', 'no network in the loop'],
    ];
    c4.callouts = rows.map(([ic, h1, h2], i) => {
      const y = 330 + i * 140;
      const g = hide(E('g', null, a), { opacity: 0, x: 24 });
      const icg = E('g', { transform: `translate(${950},${y - 12})` }, g);
      drawIcon(icg, ic);
      T(g, 1020, y, h1, { 'font-size': 40, 'font-weight': 700 });
      T(g, 1020, y + 42, h2, { 'font-size': 28, fill: DIM });
      return g;
    });
  }
  function placeChevrons(t) {
    // t in "chevron cycles"; three chevrons spaced a third of a cycle apart, near -> far
    c4.chevs.forEach((ch, i) => {
      const u = ((t + i / 3) % 1 + 1) % 1;
      const z = 1 + u * 5;
      const y = HZ + (NEARY - HZ) / z;
      const s = 1.2 / z;
      const op = u < 0.12 ? u / 0.12 : u > 0.62 ? clamp01((0.8 - u) / 0.18) : 1;
      ch.setAttribute('transform', `translate(${VPX},${y.toFixed(2)}) scale(${s.toFixed(4)},${(s * 0.8).toFixed(4)})`);
      ch.setAttribute('opacity', op.toFixed(3));
    });
  }

  // ================================================================ CHAPTER 5: glasses + watch
  CH[4] = chapter(4, 'Hands-free: glasses and watch.',
    ['The glasses look, the phone recognises, the glasses speak.', 'The watch buzzes every turn.'],
    ['Meta Wearables Device Access Toolkit', 'burst 2–3 stills · then speak', 'Wear OS Data Layer', 'RouteStep < 1 s', '8 haptic patterns']);
  const c5 = {};
  const G5 = [430, 470], PH5 = [960, 470], W5 = PORT ? [960, 905] : [1480, 470];
  const ARC_UP = [[575, 448], [712, 360], [872, 430]];
  const ARC_DN = [[872, 520], [712, 610], [575, 500]];
  {
    const a = CH[4].art;
    // glasses
    c5.glasses = hide(E('g', null, a), { opacity: 0, y: 20 });
    drawGlasses(c5.glasses, G5[0], G5[1], 1.3);
    c5.cam = E('circle', { cx: G5[0] + 102, cy: G5[1] - 30, r: 7, fill: C.warm, opacity: 0.25 }, c5.glasses);
    c5.camOn = hide(E('circle', { cx: G5[0] + 102, cy: G5[1] - 30, r: 9, fill: C.warm, filter: 'url(#ex-glow)' }, c5.glasses));
    T(c5.glasses, G5[0], 690, 'Ray-Ban Meta Gen 2', { 'font-size': 24, fill: DIM, 'text-anchor': 'middle' });
    // phone
    c5.phone = hide(E('g', null, a), { opacity: 0, y: 20 });
    const [pcx, pcy] = PH5;
    E('rect', { x: pcx - 90, y: pcy - 160, width: 180, height: 320, rx: 30, fill: 'url(#ex-phone)', stroke: C.clay, 'stroke-width': 3.5 }, c5.phone);
    E('rect', { x: pcx - 20, y: pcy - 152, width: 40, height: 5, rx: 2.5, fill: 'rgba(255,255,255,0.3)' }, c5.phone);
    if (PORT) T(c5.phone, pcx + 108, pcy + 8, 'Galaxy S25 Ultra', { 'font-size': 24, fill: DIM });
    else T(c5.phone, pcx, 690, 'Galaxy S25 Ultra', { 'font-size': 24, fill: DIM, 'text-anchor': 'middle' });
    // phone recognition: sign + scan brackets + result chip
    c5.sign = hide(E('g', null, a), { opacity: 0, scale: 0.85, transformOrigin: '50% 50%' });
    E('rect', { x: pcx - 66, y: pcy - 80, width: 132, height: 50, rx: 8, fill: 'rgba(122,208,224,0.12)', stroke: 'rgba(255,255,255,0.7)', 'stroke-width': 2 }, c5.sign);
    T(c5.sign, pcx, pcy - 47, 'ELEVATOR', { 'font-size': 20, 'font-weight': 700, 'text-anchor': 'middle' });
    c5.scan = hide(E('g', { fill: 'none', stroke: C.teal, 'stroke-width': 3.5, 'stroke-linecap': 'round' }, a), { opacity: 0, scale: 1.3, transformOrigin: '50% 50%' });
    {
      const x0 = pcx - 78, x1 = pcx + 78, y0 = pcy - 92, y1 = pcy - 18, L = 16;
      E('path', { d: `M${x0},${y0 + L} L${x0},${y0} L${x0 + L},${y0} M${x1 - L},${y0} L${x1},${y0} L${x1},${y0 + L} ` +
        `M${x0},${y1 - L} L${x0},${y1} L${x0 + L},${y1} M${x1 - L},${y1} L${x1},${y1} L${x1},${y1 - L}` }, c5.scan);
    }
    c5.found = hide(E('g', null, a), { opacity: 0, y: 10 });
    E('rect', { x: pcx - 76, y: pcy + 2, width: 152, height: 40, rx: 20, fill: C.teal }, c5.found);
    E('path', { d: `M${pcx - 60},${pcy + 22} L${pcx - 53},${pcy + 29} L${pcx - 41},${pcy + 15}`, fill: 'none', stroke: C.ink, 'stroke-width': 3.5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, c5.found);
    fitL(T(c5.found, pcx - 30, pcy + 29, 'sign read', { 'font-size': 19, 'font-weight': 700, fill: C.ink }), 96);
    c5.stepCard = hide(E('g', null, a), { opacity: 0, y: 10 });
    E('rect', { x: pcx - 70, y: pcy + 58, width: 140, height: 72, rx: 16, fill: C.clay }, c5.stepCard);
    E('path', { d: `M${pcx + 8},${pcy + 76} L${pcx - 10},${pcy + 94} L${pcx + 8},${pcy + 112}`, fill: 'none', stroke: C.ink, 'stroke-width': 6, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, c5.stepCard);

    // watch
    c5.watch = hide(E('g', null, a), { opacity: 0, y: 20 });
    const [wx, wy] = W5, wr = 72;
    c5.rings = [0, 1, 2, 3].map(() => E('circle', { cx: wx, cy: wy, r: wr, fill: 'none', stroke: C.gold, 'stroke-width': 3.5, opacity: 0 }, c5.watch));
    E('rect', { x: wx - 30, y: wy - wr - 44, width: 60, height: 44, rx: 10, fill: 'rgba(255,255,255,0.18)' }, c5.watch);
    E('rect', { x: wx - 30, y: wy + wr, width: 60, height: 44, rx: 10, fill: 'rgba(255,255,255,0.18)' }, c5.watch);
    E('circle', { cx: wx, cy: wy, r: wr, fill: '#241d1a', stroke: 'rgba(255,255,255,0.5)', 'stroke-width': 5 }, c5.watch);
    E('circle', { cx: wx, cy: wy, r: wr - 15, fill: C.ink, stroke: 'rgba(255,255,255,0.85)', 'stroke-width': 2.5 }, c5.watch);
    c5.wchev = hide(E('path', { d: `M${wx + 10},${wy - 24} L${wx - 16},${wy} L${wx + 10},${wy + 24}`, fill: 'none', stroke: C.gold,
      'stroke-width': 11, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, c5.watch), { opacity: 0, x: 14 });
    T(c5.watch, wx, PORT ? wy + wr + 44 + 48 : 690, 'Galaxy Watch 8 Classic', { 'font-size': 24, fill: DIM, 'text-anchor': 'middle' });

    // look arc (glasses -> phone), speak arc (phone -> glasses)
    const arcD = (A) => `M${A[0][0]},${A[0][1]} Q${A[1][0]},${A[1][1]} ${A[2][0]},${A[2][1]}`;
    c5.arcUp = E('path', { d: arcD(ARC_UP), fill: 'none', stroke: 'rgba(255,182,140,0.7)', 'stroke-width': 2.5, 'stroke-dasharray': '6 9', 'stroke-linecap': 'round' }, a);
    c5.arcDn = E('path', { d: arcD(ARC_DN), fill: 'none', stroke: 'rgba(122,208,224,0.7)', 'stroke-width': 2.5, 'stroke-dasharray': '6 9', 'stroke-linecap': 'round' }, a);
    hide(c5.arcUp); hide(c5.arcDn);
    c5.lookTx = hide(T(a, 712, 376, 'look', { 'font-size': 26, 'font-weight': 700, fill: C.warm, 'text-anchor': 'middle' }), { opacity: 0, y: 8 });
    c5.speakTx = hide(T(a, 712, 610, 'speak', { 'font-size': 26, 'font-weight': 700, fill: C.teal, 'text-anchor': 'middle' }), { opacity: 0, y: 8 });
    // burst frames
    c5.frames = [0, 1, 2].map(() => {
      const f = E('g', { opacity: 0 }, a);
      E('rect', { x: -32, y: -23, width: 64, height: 46, rx: 6, fill: 'rgba(255,182,140,0.28)', stroke: C.warm, 'stroke-width': 2.5 }, f);
      E('rect', { x: -18, y: -7, width: 36, height: 12, rx: 2, fill: 'none', stroke: 'rgba(255,255,255,0.8)', 'stroke-width': 1.8 }, f);
      return f;
    });
    c5.voice = E('circle', { r: 10, fill: C.teal, filter: 'url(#ex-glow)', opacity: 0 }, a);
    // speech bubble
    c5.bubble = hide(E('g', null, a), { opacity: 0, scale: 0.6, transformOrigin: '50% 100%' });
    {
      const bx = 160, by = 244, bw = 540, bh = 86, tx = G5[0];
      E('path', { d: `M${bx + 22},${by} L${bx + bw - 22},${by} Q${bx + bw},${by} ${bx + bw},${by + 22} L${bx + bw},${by + bh - 22} ` +
        `Q${bx + bw},${by + bh} ${bx + bw - 22},${by + bh} L${tx + 22},${by + bh} L${tx - 2},${by + bh + 30} L${tx - 8},${by + bh} ` +
        `L${bx + 22},${by + bh} Q${bx},${by + bh} ${bx},${by + bh - 22} L${bx},${by + 22} Q${bx},${by} ${bx + 22},${by} Z`,
      fill: 'rgba(20,40,44,0.72)', stroke: C.teal, 'stroke-width': 2.8, 'stroke-linejoin': 'round' }, c5.bubble);
      const t = T(c5.bubble, bx + bw / 2, by + 55, 'Turn left at the elevator', { 'font-size': 32, 'font-weight': 700, 'text-anchor': 'middle' });
      fitL(t, bw - 40);
    }
    c5.waves = [0, 1].map(k => hide(E('path', { d: `M${G5[0] - 140 - k * 16},${G5[1] - 34 - k * 10} Q${G5[0] - 158 - k * 22},${G5[1] - 8} ${G5[0] - 140 - k * 16},${G5[1] + 18 + k * 10}`,
      fill: 'none', stroke: C.teal, 'stroke-width': 3.5, 'stroke-linecap': 'round' }, a)));
    // phone -> watch link
    const L0 = PORT ? [pcx, pcy + 178] : [pcx + 106, wy];
    const L1 = PORT ? [wx, wy - wr - 44 - 16] : [wx - wr - 20, wy];
    const llen = Math.hypot(L1[0] - L0[0], L1[1] - L0[1]);
    c5.link = E('line', { x1: L0[0], y1: L0[1], x2: L1[0], y2: L1[1], stroke: C.gold, 'stroke-width': 3, 'stroke-linecap': 'round' }, a);
    c5.link.style.strokeDasharray = `${llen} ${llen}`;
    hide(c5.link, { strokeDashoffset: llen });
    c5.linkTx = hide(PORT
      ? T(a, pcx + 24, (L0[1] + L1[1]) / 2 + 9, 'RouteStep', { 'font-size': 24, 'font-weight': 700, fill: C.gold })
      : T(a, (L0[0] + L1[0]) / 2, wy - 20, 'RouteStep', { 'font-size': 24, 'font-weight': 700, fill: C.gold, 'text-anchor': 'middle' }), { opacity: 0, y: 8 });
    c5.pk = hide(E('circle', { cx: L0[0], cy: L0[1], r: 9, fill: C.gold, filter: 'url(#ex-glow)' }, a), { opacity: 0, x: 0, y: 0 });
    c5.pkd = [L1[0] - L0[0], L1[1] - L0[1]];
  }

  // ================================================================ icons
  function drawIcon(g, kind) {
    const st = { fill: 'none', stroke: C.white, 'stroke-width': 3.5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' };
    const S = (extra) => Object.assign({}, st, extra || {});
    if (kind === 'sat') {
      E('rect', { x: -12, y: -12, width: 24, height: 24, rx: 3, transform: 'rotate(45)', ...S({ fill: 'rgba(255,255,255,0.1)' }) }, g);
      E('rect', { x: -52, y: -9, width: 30, height: 18, rx: 2, ...S({ stroke: C.teal }) }, g);
      E('rect', { x: 22, y: -9, width: 30, height: 18, rx: 2, ...S({ stroke: C.teal }) }, g);
      E('path', { d: 'M-18,28 Q0,40 18,28', ...S({ stroke: C.gold }) }, g);
      E('path', { d: 'M-30,40 Q0,60 30,40', ...S({ stroke: C.gold }) }, g);
    } else if (kind === 'globe') {
      E('circle', { r: 36, ...S() }, g);
      E('ellipse', { rx: 15, ry: 36, ...S({ 'stroke-width': 2.5 }) }, g);
      E('line', { x1: -36, y1: 0, x2: 36, y2: 0, ...S({ 'stroke-width': 2.5 }) }, g);
      E('path', { d: 'M30,-10 c-11,-13 -11,-28 0,-28 c11,0 11,15 0,28 z', ...S({ fill: C.clay, stroke: C.warm, 'stroke-width': 2.5 }), transform: 'translate(4,-6)' }, g);
      E('circle', { cx: 34, cy: -40, r: 22, ...S({ stroke: C.teal, 'stroke-width': 2, 'stroke-dasharray': '4 5' }) }, g);
    } else if (kind === 'door') {
      E('rect', { x: -40, y: -42, width: 44, height: 80, rx: 3, ...S() }, g);
      E('circle', { cx: -4, cy: 0, r: 3.5, fill: C.white }, g);
      E('circle', { cx: 24, cy: 20, r: 22, ...S({ fill: 'rgba(20,16,14,0.8)', stroke: C.teal }) }, g);
      E('path', { d: 'M24,4 L30,20 L24,36 L18,20 Z', fill: C.err, transform: 'rotate(30 24 20)' }, g);
    } else if (kind === 'sign') {
      E('rect', { x: -46, y: -26, width: 92, height: 52, rx: 7, ...S({ fill: 'rgba(255,255,255,0.08)' }) }, g);
      T(g, 0, 10, '3361', { 'font-size': 28, 'font-weight': 700, 'text-anchor': 'middle' });
      E('path', { d: 'M-60,-24 L-60,-40 L-44,-40 M44,-40 L60,-40 L60,-24 M-60,24 L-60,40 L-44,40 M44,40 L60,40 L60,24', ...S({ stroke: C.teal }) }, g);
    } else if (kind === 'baro') {
      E('path', { d: 'M-38,14 A38,38 0 0 1 38,14', ...S() }, g);
      for (let i = 0; i <= 4; i++) {
        const a = Math.PI * (1 + i / 4), r0 = 38, r1 = 30;
        E('line', { x1: Math.cos(a) * r0, y1: 14 + Math.sin(a) * r0, x2: Math.cos(a) * r1, y2: 14 + Math.sin(a) * r1, ...S({ 'stroke-width': 2.5 }) }, g);
      }
      E('line', { x1: 0, y1: 14, x2: 18, y2: -12, ...S({ stroke: C.gold, 'stroke-width': 4 }) }, g);
      E('circle', { cx: 0, cy: 14, r: 5, fill: C.gold }, g);
      T(g, 0, 50, 'F6', { 'font-size': 22, 'font-weight': 700, fill: C.teal, 'text-anchor': 'middle' });
    } else if (kind === 'pin') {
      E('ellipse', { cx: 0, cy: 26, rx: 24, ry: 7, ...S({ stroke: C.teal, 'stroke-width': 2.5 }) }, g);
      E('path', { d: 'M0,24 C-20,2 -22,-10 -22,-14 C-22,-28 -12,-36 0,-36 C12,-36 22,-28 22,-14 C22,-10 20,2 0,24 Z', ...S({ fill: C.clay, stroke: C.warm }) }, g);
      E('circle', { cx: 0, cy: -14, r: 7, fill: C.ink }, g);
    } else if (kind === 'ruler') {
      E('rect', { x: -34, y: -12, width: 68, height: 26, rx: 4, ...S({ fill: 'rgba(255,209,102,0.15)', stroke: C.gold }) }, g);
      for (let i = 1; i < 6; i++) E('line', { x1: -34 + i * 11.3, y1: -12, x2: -34 + i * 11.3, y2: i % 2 ? -2 : 4, ...S({ stroke: C.gold, 'stroke-width': 2 }) }, g);
      T(g, 0, 40, '1 m', { 'font-size': 18, 'font-weight': 700, fill: C.gold, 'text-anchor': 'middle' });
    } else if (kind === 'cloud') {
      E('path', { d: 'M-20,14 C-32,14 -33,-1 -22,-3 C-22,-15 -9,-19 -2,-9 C2,-20 19,-19 19,-5 C28,-5 29,14 19,14 Z', ...S() }, g);
      E('line', { x1: -28, y1: -20, x2: 26, y2: 22, stroke: C.err, 'stroke-width': 4.5, 'stroke-linecap': 'round' }, g);
    }
  }
  function drawGlasses(parent, gx, gy, s) {
    const gl = E('g', { transform: `translate(${gx},${gy}) scale(${s})`, fill: 'rgba(255,255,255,0.08)', stroke: C.white,
      'stroke-width': 4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, parent);
    E('path', { d: 'M-78,-8 C-78,-26 -70,-30 -52,-30 L-26,-30 C-10,-30 -8,-20 -12,0 C-16,20 -30,30 -48,30 C-68,30 -78,16 -78,-8 Z' }, gl);
    E('path', { d: 'M78,-8 C78,-26 70,-30 52,-30 L26,-30 C10,-30 8,-20 12,0 C16,20 30,30 48,30 C68,30 78,16 78,-8 Z' }, gl);
    E('path', { d: 'M-12,-16 C-6,-26 6,-26 12,-16', fill: 'none' }, gl);
    E('path', { d: 'M-78,-18 L-98,-24', fill: 'none' }, gl);
    E('path', { d: 'M78,-18 L98,-24', fill: 'none' }, gl);
    return gl;
  }

  // ================================================================ timeline
  let ctx = null, master = null;

  function enter(tl, ch, T0) {
    tl.set(ch.g, { opacity: 1 }, T0 + 0.25);
    tl.to(ch.kick, { opacity: 1, y: 0, duration: 0.5, ease: 'power3.out' }, T0 + 0.3);
    tl.to(ch.ttl, { opacity: 1, y: 0, duration: 0.65, ease: 'power3.out' }, T0 + 0.36);
    tl.to(ch.cap, { opacity: 1, y: 0, duration: 0.6, ease: 'power2.out' }, T0 + 1.3);
    if (ch.chips.length) tl.to(ch.chips, { opacity: 1, y: 0, duration: 0.45, stagger: 0.14, ease: 'back.out(1.6)' }, T0 + 2.2);
  }
  function dot(tl, i, T0) {
    if (i > 0) tl.to(dotOn[i - 1], { opacity: 0, scale: 0.4, duration: 0.3 }, T0 + 0.2);
    tl.to(dotOn[i], { opacity: 1, scale: 1, duration: 0.4, ease: 'back.out(2)' }, T0 + 0.3);
  }

  // Each chapter's build, relative to its start T0 (title and chips are handled by enter()).
  const BUILD = [
    (tl, T0) => {
    tl.to(gSlab, { opacity: 1, duration: 0.6, ease: 'power1.out' }, T0 + 0.7);
    for (const e of edgeEls) {
      tl.to(e.ln, { strokeDashoffset: 0, duration: 0.45, ease: 'power1.inOut' }, T0 + 1.0 + (e.d0 / maxD) * 1.7);
    }
    firstRun.order.forEach(id => {
      tl.to(nodeEls[id], { opacity: 1, scale: 1, duration: 0.4, ease: 'back.out(2.2)' }, T0 + 0.9 + (firstRun.dist[id] / maxD) * 1.9);
    });
    tl.to(door, { opacity: 1, duration: 0.3 }, T0 + 2.2);
    tl.to(gNodeLab, { opacity: 1, duration: 0.5 }, T0 + 3.0);
    tl.to(c1.nodeLine, { strokeDashoffset: 0, duration: 0.35 }, T0 + 3.1);
    tl.to(c1.nodeTx, { opacity: 1, y: 0, duration: 0.4 }, T0 + 3.2);
    tl.to(c1.edgeHi, { strokeDashoffset: 0, duration: 0.45 }, T0 + 3.4);
    tl.to(c1.edgeTx, { opacity: 1, y: 0, duration: 0.4 }, T0 + 3.55);
    tl.to(c1.legend, { opacity: 1, y: 0, duration: 0.5 }, T0 + 3.8);

    },
    (tl, T0) => {
    tl.to(c2.clock, { opacity: 1, y: 0, duration: 0.45, ease: 'power2.out' }, T0 + 0.8);
    firstRun.order.forEach(id => {
      const at = T0 + 1.0 + (firstRun.dist[id] / maxD) * 1.4;
      tl.fromTo(c2.rings[id], { attr: { r: 16 }, opacity: 0.95 }, { attr: { r: 50 }, opacity: 0, duration: 0.7, ease: 'power2.out', immediateRender: false }, at);
      tl.to(c2.lit[id], { opacity: 0.8, duration: 0.25 }, at);
    });
    tl.to(Object.values(c2.lit), { opacity: 0, duration: 0.4 }, T0 + 2.6);
    tl.to(c2.p1.pe, { strokeDashoffset: 0, duration: 0.7, ease: 'power2.inOut' }, T0 + 2.5);
    tl.to(c2.read, { opacity: 1, y: 0, duration: 0.4 }, T0 + 2.9);
    // time flips
    tl.to(c2.t1, { scaleY: 0, transformOrigin: '50% 50%', duration: 0.18, ease: 'power2.in' }, T0 + 3.5);
    tl.to(c2.t2, { scaleY: 1, duration: 0.22, ease: 'power2.out' }, T0 + 3.68);
    tl.to(c2.doorRed, { opacity: 1, duration: 0.2 }, T0 + 3.9);
    tl.to(c2.lock, { opacity: 1, scale: 1, duration: 0.45, ease: 'back.out(2.4)' }, T0 + 3.9);
    tl.to(c2.lockTx, { opacity: 1, y: 0, duration: 0.35 }, T0 + 4.05);
    tl.to(c2.p1red.pe, { opacity: 1, duration: 0.2 }, T0 + 4.0);
    tl.to([c2.p1.pe, c2.p1red.pe], { opacity: 0, duration: 0.45 }, T0 + 4.35);
    tl.to(c2.p2.pe, { strokeDashoffset: 0, duration: 0.9, ease: 'power2.inOut' }, T0 + 4.5);
    tl.to([c2.rl1, c2.rv1], { opacity: 0, duration: 0.2 }, T0 + 4.9);
    tl.to([c2.rl2, c2.rv2], { opacity: 1, duration: 0.3 }, T0 + 5.05);

    },
    (tl, T0) => {
    tl.to(c3.cards.map(c => c.g), { opacity: 1, y: 0, duration: 0.5, stagger: 0.1, ease: 'power2.out' }, T0 + 0.75);
    tl.to(c3.brackets, { opacity: 1, y: 0, duration: 0.4, stagger: 0.12 }, T0 + 1.1);
    tl.to(c3.you, { opacity: 1, duration: 0.3 }, T0 + 1.3);
    c3.cards.forEach((c, i) => {
      const at = T0 + 1.4 + i * 0.55;
      if (i > 0) tl.to(c3.you, { [c3.axis]: c.pos, duration: 0.45, ease: 'power2.inOut' }, at - 0.3);
      tl.to(c.lit, { opacity: 1, duration: 0.3 }, at);
      tl.to(c.numOn, { opacity: 1, duration: 0.3 }, at);
      tl.to(c.icon, { opacity: 1, duration: 0.3 }, at);
      if (c.arrow) tl.to(c.arrow, { opacity: 1, duration: 0.3 }, at + 0.25);
    });

    },
    (tl, T0) => {
    tl.to(c4.phone, { opacity: 1, scale: 1, duration: 0.6, ease: 'power3.out' }, T0 + 0.7);
    tl.to(c4.walls, { opacity: 1, duration: 0.5 }, T0 + 1.0);
    tl.to(c4.grid, { opacity: 1, duration: 0.5 }, T0 + 1.2);
    const flow = { t: 0 };
    const flowDur = SINGLE === null ? 5.6 : SINGLE_LEN - 1.4;
    tl.to(flow, { t: 3.5 * flowDur / 5.6, duration: flowDur, ease: 'none', onUpdate: () => placeChevrons(flow.t) }, T0 + 1.4);
    tl.to(c4.turn, { opacity: 1, scale: 1, duration: 0.5, ease: 'back.out(2)' }, T0 + 2.2);
    tl.to(c4.banner, { opacity: 1, y: 0, duration: 0.4 }, T0 + 2.4);
    tl.to(c4.card, { opacity: 1, y: 0, duration: 0.4 }, T0 + 2.6);
    tl.to(c4.callouts, { opacity: 1, x: 0, duration: 0.5, stagger: 0.3, ease: 'power2.out' }, T0 + 1.6);

    },
    (tl, T0) => {
    tl.to([c5.glasses, c5.phone, c5.watch], { opacity: 1, y: 0, duration: 0.5, stagger: 0.15, ease: 'power2.out' }, T0 + 0.65);
    // look: burst of three stills
    tl.to(c5.camOn, { opacity: 1, duration: 0.15 }, T0 + 1.2);
    tl.to([c5.arcUp, c5.lookTx], { opacity: 1, y: 0, duration: 0.3 }, T0 + 1.2);
    c5.frames.forEach((f, i) => {
      const pr = { u: 0 };
      tl.to(pr, { u: 1, duration: 0.7, ease: 'power1.inOut', onUpdate: () => {
        const [x, y] = quad(ARC_UP[0], ARC_UP[1], ARC_UP[2], pr.u);
        const s = 1 - 0.35 * pr.u;
        f.setAttribute('transform', `translate(${x.toFixed(1)},${y.toFixed(1)}) scale(${s.toFixed(3)})`);
        f.setAttribute('opacity', (pr.u <= 0 ? 0 : pr.u < 0.1 ? pr.u * 10 : pr.u > 0.85 ? (1 - pr.u) / 0.15 : 1).toFixed(3));
      } }, T0 + 1.3 + i * 0.16);
    });
    tl.to(c5.camOn, { opacity: 0, duration: 0.2 }, T0 + 2.0);   // camera stops before audio
    // recognise
    tl.to(c5.sign, { opacity: 1, scale: 1, duration: 0.3 }, T0 + 2.05);
    tl.to(c5.scan, { opacity: 1, scale: 1, duration: 0.35, ease: 'power2.out' }, T0 + 2.15);
    tl.to(c5.found, { opacity: 1, y: 0, duration: 0.3, ease: 'back.out(2)' }, T0 + 2.45);
    // speak
    tl.to([c5.arcDn, c5.speakTx], { opacity: 1, y: 0, duration: 0.3 }, T0 + 2.6);
    const vp = { u: 0 };
    tl.to(vp, { u: 1, duration: 0.55, ease: 'power1.inOut', onUpdate: () => {
      const [x, y] = quad(ARC_DN[0], ARC_DN[1], ARC_DN[2], vp.u);
      c5.voice.setAttribute('cx', x.toFixed(1)); c5.voice.setAttribute('cy', y.toFixed(1));
      c5.voice.setAttribute('opacity', (vp.u <= 0 || vp.u >= 1 ? 0 : 1).toString());
    } }, T0 + 2.65);
    tl.to(c5.bubble, { opacity: 1, scale: 1, duration: 0.45, ease: 'back.out(1.8)' }, T0 + 3.15);
    tl.to(c5.waves, { opacity: 1, duration: 0.2, stagger: 0.12 }, T0 + 3.2);
    tl.to(c5.waves, { opacity: 0.35, duration: 0.3, stagger: 0.12 }, T0 + 3.7);
    // watch
    tl.to(c5.stepCard, { opacity: 1, y: 0, duration: 0.3 }, T0 + 3.2);
    tl.to(c5.link, { strokeDashoffset: 0, duration: 0.4, ease: 'power2.out' }, T0 + 3.3);
    tl.to(c5.linkTx, { opacity: 1, y: 0, duration: 0.3 }, T0 + 3.4);
    tl.to(c5.pk, { opacity: 1, duration: 0.1 }, T0 + 3.5);
    tl.to(c5.pk, { x: c5.pkd[0], y: c5.pkd[1], duration: 0.45, ease: 'power1.inOut' }, T0 + 3.5);
    tl.to(c5.pk, { opacity: 0, duration: 0.1 }, T0 + 3.9);
    tl.to(c5.wchev, { opacity: 1, x: 0, duration: 0.35, ease: 'back.out(2)' }, T0 + 3.95);
    [T0 + 4.0, T0 + 4.22, T0 + 5.3, T0 + 5.52].forEach((at, i) => {
      tl.fromTo(c5.rings[i], { attr: { r: 72 }, opacity: 0.9 }, { attr: { r: 128 }, opacity: 0, duration: 0.9, ease: 'power2.out', immediateRender: false }, at);
    });
    tl.to(c5.wchev, { x: -6, duration: 0.15, yoyo: true, repeat: 1, ease: 'sine.inOut' }, T0 + 4.4);

    },
  ];
  // Graph fully drawn (used when chapter 2 plays on its own).
  function showGraph() {
    gsap.set(gSlab, { opacity: 1 });
    for (const e of edgeEls) gsap.set(e.ln, { strokeDashoffset: 0 });
    gsap.set(Object.values(nodeEls), { opacity: 1, scale: 1 });
    gsap.set([door, gNodeLab], { opacity: 1 });
  }

  function build() {
    for (const [elx, vars] of hidden) gsap.set(elx, vars);
    placeChevrons(0);
    master = gsap.timeline({ paused: true });
    const tl = master;

    if (SINGLE !== null) {
      gsap.set(root, { opacity: 0 });
      tl.to(root, { opacity: 1, duration: 0.4, ease: 'power1.out' }, 0);
      if (SINGLE === 1) showGraph();
      if (SINGLE >= 2) gsap.set(Lgraph, { opacity: 0 });
      enter(tl, CH[SINGLE], 0);
      BUILD[SINGLE](tl, 0);
      tl.to(root, { opacity: 0, duration: 0.5, ease: 'power1.in' }, SINGLE_LEN - 0.5);
      tl.set({}, {}, SINGLE_LEN);
      return;
    }

    tl.to(Ldots, { opacity: 1, duration: 0.5 }, 0.1);
    for (let i = 0; i < N_CH; i++) {
      const T0 = i * CH_LEN;
      if (i === 2) tl.to([CH[1].g, Lgraph], { opacity: 0, duration: 0.4 }, T0);
      else if (i > 0) tl.to(CH[i - 1].g, { opacity: 0, duration: 0.4 }, T0);
      enter(tl, CH[i], i === 0 ? T0 - 0.1 : T0);
      dot(tl, i, T0);
      BUILD[i](tl, T0);
    }
    tl.to(root, { opacity: 0, duration: DURATION - END_FADE, ease: 'power1.in' }, END_FADE);
    tl.set({}, {}, DURATION);
  }

  L(layoutFrame);
  ctx = gsap.context(build, svg);
  if (document.fonts && document.fonts.load) {
    Promise.all(['400', '500', '600', '700'].map(w => document.fonts.load(`${w} 30px Sora`)))
      .then(() => layouts.forEach(f => f())).catch(() => {});
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
  const total = SINGLE === null ? DURATION : SINGLE_LEN;
  return { play, pause, destroy, seek, duration: total, chapters: SINGLE === null ? [0, 7, 14, 21, 28] : [0], aspect: ASPECT, chapter: SINGLE };
}

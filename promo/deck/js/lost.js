// Problem slide: "finding the class is hard". A plan view where corridors look alike, a student
// wanders (dead end, backtrack, wrong floor) while a clock runs to 9:40; then the clean route
// (door, elevator, room 3361 on floor 3) takes 1:27. Pure SVG + global gsap, deterministic.
// API: mount(el, opts) -> { play, pause, destroy, seek(seconds), duration }.
const NS = 'http://www.w3.org/2000/svg';
const mk = (tag, attrs = {}, parent) => {
  const n = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
  if (parent) parent.appendChild(n);
  return n;
};

const C = {
  ink: '#1b1614', plate: '#251e1b', white: '#fff8f4', clay: '#c67c4e', warm: '#ffb68c',
  teal: '#7ad0e0', gold: '#ffd166', red: '#e05a4e',
  line: 'rgba(255,255,255,.16)', floor: 'rgba(255,255,255,.055)', dim: 'rgba(255,248,244,.62)',
};

// Timeline marks (seconds).
const T = {
  wStart: 0.5, wDead: 1.75, wBack: 2.05, wStairs: 2.7, clockEnd: 3.1,
  b2: 3.3, b3: 5.0, r1: 5.3, r2: 6.0, r3: 6.5, rEnd: 7.3, cap: 7.6, end: 8.4,
};
const LOST_S = 9 * 60 + 40, GUIDED_S = 60 + 27;

// Polyline helpers (exact lengths, no DOM needed).
const segLen = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);
const polyLen = (pts) => pts.slice(1).reduce((s, p, i) => s + segLen(pts[i], p), 0);
function polyAt(pts, d) {
  for (let i = 1; i < pts.length; i++) {
    const l = segLen(pts[i - 1], pts[i]);
    if (d <= l || i === pts.length - 1) {
      const k = l ? Math.max(0, Math.min(1, d / l)) : 0;
      return [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * k, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * k];
    }
    d -= l;
  }
  return pts[pts.length - 1];
}
const polyD = (pts) => 'M' + pts.map(p => p.join(' ')).join(' L');
const clamp01 = (x) => Math.max(0, Math.min(1, x));
const lerp = (a, b, k) => a + (b - a) * k;
function mixHex(h1, h2, k) {
  const p = (h) => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
  const a = p(h1), b = p(h2);
  return '#' + a.map((v, i) => Math.round(lerp(v, b[i], k)).toString(16).padStart(2, '0')).join('');
}

// Wander: door -> down the west side -> along the bottom -> up -> east wing (dead end) -> back -> stairs.
const WANDER_A = [[30, 310], [130, 310], [130, 410], [470, 410], [470, 310], [640, 310]];
const WANDER_B = [[640, 310], [470, 310], [470, 210], [525, 210]];
const WANDER = [...WANDER_A, ...WANDER_B.slice(1)];
const LA = polyLen(WANDER_A), LW = polyLen(WANDER);
// Guided route: door -> west corridor -> top corridor -> elevator | ride | floor 3 corridor -> 3361.
const R1 = [[30, 310], [130, 310], [130, 210], [300, 210], [300, 168]];
const R3 = [[930, 262], [930, 310], [1290, 310], [1290, 402]];
const ARC = 'M300 168 C 380 40, 840 40, 930 262';

let mountCount = 0;
export function mount(root, opts = {}) {
  root.innerHTML = '';
  const svg = mk('svg', { viewBox: '0 0 1400 620', preserveAspectRatio: 'xMidYMid meet',
    style: 'width:100%;height:100%;display:block;overflow:visible;font-family:Sora,sans-serif' }, root);

  const chip = (parent, { x, y, text, color, size = 21, glyph, anchor = 'middle' }) => {
    const g = mk('g', { transform: `translate(${x},${y})` }, parent);
    const w = text.length * size * 0.6 + (glyph ? size * 1.5 : 0) + 30, h = size + 20;
    const x0 = anchor === 'middle' ? -w / 2 : anchor === 'end' ? -w : 0;
    mk('rect', { x: x0, y: -h / 2, width: w, height: h, rx: h / 2, fill: C.ink, 'fill-opacity': .94, stroke: color, 'stroke-width': 2 }, g);
    let tx = x0 + 15;
    if (glyph) {
      const r = size * 0.48, gx = tx + r;
      mk('circle', { cx: gx, cy: 0, r, fill: color }, g);
      if (glyph === 'x') mk('path', { d: `M${gx - r * .4} ${-r * .4} L${gx + r * .4} ${r * .4} M${gx + r * .4} ${-r * .4} L${gx - r * .4} ${r * .4}`, stroke: C.ink, 'stroke-width': 3, 'stroke-linecap': 'round' }, g);
      if (glyph === 'up') mk('path', { d: `M${gx} ${r * .5} V${-r * .45} M${gx - r * .42} ${-r * .05} L${gx} ${-r * .5} L${gx + r * .42} ${-r * .05}`, fill: 'none', stroke: C.ink, 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, g);
      tx = gx + r + 9;
    }
    const t = mk('text', { x: glyph ? tx : 0, y: size * 0.36, fill: color, 'font-size': size, 'font-weight': 500, 'text-anchor': glyph ? 'start' : 'middle' }, g);
    if (!glyph) t.setAttribute('x', x0 + w / 2);
    t.textContent = text;
    return g;
  };

  // ---------- header: beat captions + clock ----------
  const heads = ['Some buildings are confusing.', 'Places don’t lead to other places.', 'Sometimes there’s a faster way.']
    .map((s, i) => { const t = mk('text', { x: 30, y: 52, fill: i === 2 ? C.warm : C.white, 'font-size': 30, 'font-weight': 700, opacity: 0 }, svg); t.textContent = s; return t; });

  const clockG = mk('g', { transform: 'translate(1370,46)' }, svg);
  const clockLbl = mk('text', { x: -200, y: 10, fill: C.dim, 'font-size': 20, 'font-weight': 500, 'text-anchor': 'end' }, clockG);
  const face = mk('g', { transform: 'translate(-172,0)' }, clockG);
  const faceRing = mk('circle', { r: 17, fill: 'none', stroke: C.white, 'stroke-width': 3 }, face);
  const hand = mk('line', { x1: 0, y1: 0, x2: 0, y2: -11, stroke: C.white, 'stroke-width': 3, 'stroke-linecap': 'round' }, face);
  // Fixed slots for each character = tabular digits whatever the font does.
  const slotX = [-124, -96, -73, -50, -18];
  const slots = slotX.map(x => mk('text', { x, y: 16, fill: C.white, 'font-size': 46, 'font-weight': 700, 'text-anchor': 'middle' }, clockG));

  // ---------- world (plates, trails, student) ----------
  const world = mk('g', { transform: 'translate(0,12)' }, svg);
  const plates = mk('g', {}, world);

  // Floor 1
  const f1 = mk('g', {}, plates);
  mk('text', { x: 34, y: 90, fill: C.dim, 'font-size': 20, 'font-weight': 500 }, f1).textContent = 'Floor 1 · you are here';
  mk('rect', { x: 30, y: 100, width: 770, height: 400, rx: 14, fill: C.plate, stroke: 'rgba(255,255,255,.2)', 'stroke-width': 2 }, f1);
  const cor = (x, y, w, h, p = f1) => mk('rect', { x, y, width: w, height: h, fill: C.floor }, p);
  cor(110, 190, 380, 40); cor(110, 390, 380, 40); cor(110, 230, 40, 160); cor(450, 230, 40, 160); // loop
  cor(30, 290, 80, 40); // entrance stub
  const wing = cor(490, 290, 170, 40); // east wing, dead end at x=660
  const annex = cor(680, 290, 120, 40); // annex, reached only from the east door
  const room = (x, y, w, h, p = f1) => mk('rect', { x, y, width: w, height: h, fill: 'none', stroke: C.line, 'stroke-width': 1.5 }, p);
  [[110, 110, 80, 80], [190, 110, 80, 80], [340, 110, 75, 80], [415, 110, 75, 80],
    [150, 230, 100, 160], [250, 230, 100, 160], [350, 230, 100, 160],
    [110, 430, 95, 62], [205, 430, 95, 62], [300, 430, 95, 62], [395, 430, 95, 62],
    [560, 110, 100, 180], [490, 330, 85, 162], [575, 330, 85, 162],
    [680, 110, 112, 180], [680, 330, 112, 162]].forEach(r => room(...r));
  // Identical-looking room numbers make the "everything looks the same" point.
  [['1102', 150, 157], ['1104', 230, 157], ['1106', 377, 157], ['1108', 452, 157], ['1110', 200, 317], ['1110', 400, 317], ['1112', 532, 418], ['1112', 736, 418]]
    .forEach(([s, x, y]) => { mk('text', { x, y, fill: 'rgba(255,248,244,.34)', 'font-size': 20, 'text-anchor': 'middle' }, f1).textContent = s; });
  // Stairs (hatched), top-right end of the top corridor.
  const stairs = mk('g', {}, f1);
  mk('rect', { x: 490, y: 110, width: 70, height: 120, fill: 'rgba(255,255,255,.04)', stroke: C.line, 'stroke-width': 1.5 }, stairs);
  for (let y = 122; y < 228; y += 13) mk('line', { x1: 498, y1: y, x2: 552, y2: y, stroke: 'rgba(255,255,255,.22)', 'stroke-width': 2 }, stairs);
  // Elevator (teal square) above the top corridor.
  const elev1 = mk('rect', { x: 280, y: 148, width: 40, height: 40, rx: 4, fill: 'rgba(122,208,224,.2)', stroke: C.teal, 'stroke-width': 2.5 }, f1);
  const elevLbl1 = mk('text', { x: 300, y: 138, fill: C.teal, 'font-size': 20, 'text-anchor': 'middle' }, f1);
  elevLbl1.textContent = 'elevator';
  // Doors.
  const door = (x, y, p = f1) => mk('circle', { cx: x, cy: y, r: 11, fill: C.ink, stroke: '#fff', 'stroke-width': 3 }, p);
  door(30, 310); door(800, 310);

  // Floor 3
  const f3 = mk('g', {}, plates);
  mk('text', { x: 854, y: 90, fill: C.dim, 'font-size': 20, 'font-weight': 500 }, f3).textContent = 'Floor 3 · your class';
  mk('rect', { x: 850, y: 100, width: 520, height: 400, rx: 14, fill: C.plate, stroke: 'rgba(255,255,255,.2)', 'stroke-width': 2 }, f3);
  cor(870, 290, 480, 40, f3);
  [[870, 110, 120, 110], [990, 110, 120, 180], [1110, 110, 120, 180], [1230, 110, 120, 180], [960, 230, 30, 60],
    [870, 330, 120, 162], [990, 330, 120, 162], [1110, 330, 120, 162]].forEach(r => room(...r, f3));
  const elev3 = mk('rect', { x: 910, y: 240, width: 40, height: 40, rx: 4, fill: 'rgba(122,208,224,.2)', stroke: C.teal, 'stroke-width': 2.5 }, f3);
  mk('text', { x: 930, y: 372, fill: C.teal, 'font-size': 20, 'text-anchor': 'middle' }, f3).textContent = 'elevator';
  const dest = mk('rect', { x: 1230, y: 330, width: 120, height: 162, fill: 'rgba(255,182,140,.12)', stroke: C.warm, 'stroke-width': 2.5 }, f3);
  mk('text', { x: 1290, y: 468, fill: C.warm, 'font-size': 28, 'font-weight': 700, 'text-anchor': 'middle' }, f3).textContent = '3361';

  // ---------- beat 1: wander ----------
  const maskId = 'lostTrailMask' + (++mountCount);
  const defs = mk('defs', {}, svg);
  const mask = mk('mask', { id: maskId, maskUnits: 'userSpaceOnUse', x: 0, y: 0, width: 1400, height: 620 }, defs);
  const maskPath = mk('path', { d: polyD(WANDER), fill: 'none', stroke: '#fff', 'stroke-width': 16, 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
    'stroke-dasharray': `${LW} ${LW + 10}`, 'stroke-dashoffset': LW }, mask);
  const trail = mk('path', { d: polyD(WANDER), fill: 'none', stroke: 'rgba(255,248,244,.75)', 'stroke-width': 5, 'stroke-dasharray': '0.1 13',
    'stroke-linecap': 'round', 'stroke-linejoin': 'round', mask: `url(#${maskId})` }, world);

  const deadX = mk('g', { transform: 'translate(660,310)' }, world);
  mk('line', { x1: 0, y1: -24, x2: 0, y2: 24, stroke: C.red, 'stroke-width': 5, 'stroke-linecap': 'round' }, deadX);
  const deadChip = chip(world, { x: 604, y: 372, text: 'this hallway does not connect', color: C.red, glyph: 'x', size: 20 });
  const floorChip = chip(world, { x: 560, y: 104, text: 'Floor 2 · wrong floor', color: C.red, glyph: 'up', size: 21 });

  // ---------- beat 2: no through-way ----------
  const b2 = mk('g', {}, world);
  const hiWing = mk('rect', { x: 490, y: 290, width: 170, height: 40, fill: 'rgba(224,90,78,.16)', stroke: C.red, 'stroke-width': 2.5, 'stroke-dasharray': '8 6' }, b2);
  const hiAnnex = mk('rect', { x: 680, y: 290, width: 120, height: 40, fill: 'rgba(224,90,78,.16)', stroke: C.red, 'stroke-width': 2.5, 'stroke-dasharray': '8 6' }, b2);
  const looks = mk('line', { x1: 500, y1: 310, x2: 790, y2: 310, stroke: 'rgba(255,248,244,.7)', 'stroke-width': 3, 'stroke-dasharray': '10 8' }, b2);
  const gap = mk('g', { transform: 'translate(670,310)' }, b2);
  mk('rect', { x: -10, y: -30, width: 20, height: 60, rx: 3, fill: C.ink }, gap);
  mk('path', { d: 'M-9 -30 L-9 30 M9 -30 L9 30', stroke: C.red, 'stroke-width': 4, 'stroke-linecap': 'round' }, gap);
  mk('path', { d: 'M-5 -12 L5 -4 L-5 4 L5 12', fill: 'none', stroke: C.red, 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, gap);
  const gapChip = chip(world, { x: 670, y: 250, text: 'no through-way', color: C.red, glyph: 'x', size: 22 });

  // ---------- beat 3: guided route ----------
  const route1 = mk('path', { d: polyD(R1), fill: 'none', stroke: C.clay, 'stroke-width': 9, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, world);
  const arc = mk('path', { d: ARC, fill: 'none', stroke: C.teal, 'stroke-width': 4, 'stroke-linecap': 'round' }, world);
  const route3 = mk('path', { d: polyD(R3), fill: 'none', stroke: C.clay, 'stroke-width': 9, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, world);
  const arcLbl = mk('text', { x: 615, y: 70, fill: C.teal, 'font-size': 22, 'font-weight': 500, 'text-anchor': 'middle' }, world);
  arcLbl.textContent = 'elevator to floor 3';
  const L1 = polyLen(R1), L3 = polyLen(R3);
  // Arc points precomputed so seek works even while the slide is display:none.
  const ARCP = (() => {
    const pts = [], P = [[300, 168], [380, 40], [840, 40], [930, 262]];
    for (let i = 0; i <= 60; i++) {
      const u = i / 60, v = 1 - u;
      pts.push([0, 1].map(k => v * v * v * P[0][k] + 3 * v * v * u * P[1][k] + 3 * v * u * u * P[2][k] + u * u * u * P[3][k]));
    }
    return pts;
  })();
  const LARC = polyLen(ARCP);
  route1.style.strokeDasharray = `${L1} ${L1 + 10}`;
  route3.style.strokeDasharray = `${L3} ${L3 + 10}`;
  const arcDash = 9;

  // ---------- student + bubble ----------
  const student = mk('g', {}, world);
  const halo = mk('circle', { r: 20, fill: 'rgba(255,248,244,.14)' }, student);
  mk('circle', { r: 11, fill: C.white, stroke: C.ink, 'stroke-width': 3 }, student);
  const bubble = mk('g', {}, student);
  const qG = mk('g', {}, bubble);
  mk('circle', { r: 18, fill: C.gold }, qG);
  mk('text', { y: 8, fill: C.ink, 'font-size': 24, 'font-weight': 700, 'text-anchor': 'middle' }, qG).textContent = '?';
  const okG = mk('g', {}, bubble);
  mk('circle', { r: 19, fill: C.clay }, okG);
  mk('path', { d: 'M-8 0 L-2 6 L9 -6', fill: 'none', stroke: '#fff', 'stroke-width': 4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, okG);

  // ---------- caption bar ----------
  const cap = mk('g', { transform: 'translate(700,578)' }, svg);
  mk('rect', { x: -420, y: -32, width: 840, height: 64, rx: 32, fill: 'rgba(255,255,255,.07)', stroke: 'rgba(255,255,255,.22)', 'stroke-width': 2 }, cap);
  const capT = mk('text', { x: 0, y: 11, fill: C.white, 'font-size': 30, 'font-weight': 500, 'text-anchor': 'middle' }, cap);
  const tspan = (txt, attrs = {}) => { const s = mk('tspan', attrs, capT); s.textContent = txt; return s; };
  tspan('Same building. ');
  tspan('9:40 lost', { fill: C.red, 'font-weight': 700 });
  tspan(', ');
  tspan('1:27 guided', { fill: C.clay, 'font-weight': 700 });
  tspan('.');

  // ---------- per-frame state that is a pure function of time ----------
  const p = { w: 0, r1: 0, r2: 0, r3: 0 }; // progress proxies tweened by the timeline
  function render(t) {
    // Trail reveal.
    maskPath.setAttribute('stroke-dashoffset', LW * (1 - p.w));
    route1.style.strokeDashoffset = L1 * (1 - p.r1);
    route3.style.strokeDashoffset = L3 * (1 - p.r3);
    const aLen = LARC * p.r2;
    arc.setAttribute('stroke-dasharray', p.r2 >= 1 ? `${arcDash} ${arcDash}` : `0 ${0}`);
    arc.style.opacity = p.r2 > 0 ? 1 : 0;
    if (p.r2 > 0 && p.r2 < 1) {
      // Dashed arc growing: build an explicit dash list up to aLen.
      const parts = []; let s = 0;
      while (s + arcDash * 2 < aLen) { parts.push(arcDash, arcDash); s += arcDash * 2; }
      parts.push(Math.max(0.01, Math.min(arcDash, aLen - s)), LARC + 50);
      arc.setAttribute('stroke-dasharray', parts.join(' '));
    }
    // Student position.
    let pos;
    if (t < T.b3) pos = polyAt(WANDER, LW * p.w);
    else if (p.r3 > 0) pos = polyAt(R3, L3 * p.r3);
    else if (p.r2 > 0) pos = polyAt(ARCP, LARC * p.r2);
    else pos = polyAt(R1, L1 * p.r1);
    const bob = t < T.rEnd ? Math.sin(t * Math.PI * 2 / 0.9) * 5 : 0;
    student.setAttribute('transform', `translate(${pos[0].toFixed(1)},${pos[1].toFixed(1)})`);
    bubble.setAttribute('transform', `translate(0,${(-38 + bob).toFixed(1)})`);
    halo.setAttribute('r', 20 + 4 * Math.sin(t * Math.PI * 2 / 1.4));
    // Clock.
    let secs, color, label;
    if (t < T.b3 - 0.15) {
      secs = Math.round(LOST_S * clamp01((t - T.wStart) / (T.clockEnd - T.wStart)));
      color = secs < 360 ? C.white : mixHex(C.white, C.red, clamp01((secs - 360) / (LOST_S - 360)));
      label = 'time lost';
    } else {
      secs = Math.round(GUIDED_S * clamp01((t - T.r1) / (T.rEnd - T.r1)));
      color = C.clay;
      label = 'guided';
    }
    const mm = Math.floor(secs / 60), ss = secs % 60;
    const chars = (mm < 10 ? ' ' + mm : String(mm)) + ':' + String(ss).padStart(2, '0');
    slots.forEach((s, i) => { s.textContent = chars[i] === ' ' ? '' : chars[i]; s.setAttribute('fill', color); });
    clockLbl.textContent = label; clockLbl.setAttribute('fill', t < T.b3 - 0.15 ? C.dim : C.clay);
    faceRing.setAttribute('stroke', color); hand.setAttribute('stroke', color);
    hand.setAttribute('transform', `rotate(${(secs / 60) * 360})`);
  }

  let tl;
  const extras = [];
  function build() {
    tl?.kill();
    Object.assign(p, { w: 0, r1: 0, r2: 0, r3: 0 });
    gsap.set(heads, { opacity: 0, y: 8 });
    gsap.set(plates, { opacity: 0, y: 16 });
    gsap.set(f3, { opacity: 1 });
    gsap.set([deadX, deadChip, floorChip, gapChip, arcLbl, cap, okG], { opacity: 0 });
    gsap.set([deadChip, floorChip, gapChip, cap], { scale: .85, transformOrigin: '50% 50%' });
    gsap.set(deadX, { scaleY: 0, transformOrigin: '50% 50%' });
    gsap.set([hiWing, hiAnnex, looks, gap], { opacity: 0 });
    gsap.set(trail, { opacity: 1 });
    gsap.set(student, { opacity: 0 });
    gsap.set(qG, { opacity: 1, scale: 1, transformOrigin: '50% 50%' });
    gsap.set(okG, { scale: .4, transformOrigin: '50% 50%' });
    gsap.set(clockG, { opacity: 0 });
    gsap.set(dest, { fill: 'rgba(255,182,140,.12)' });
    gsap.set([f1, elev1, elev3, elevLbl1], { opacity: 1 });
    gsap.set([elev1, elev3], { fill: 'rgba(122,208,224,.2)' });

    tl = gsap.timeline({ paused: true, onUpdate() { render(this.time()); } });
    tl
      // Beat 1: confusing.
      .to(heads[0], { opacity: 1, y: 0, duration: .5, ease: 'power3.out' }, 0)
      .to(plates, { opacity: 1, y: 0, duration: .5, ease: 'power3.out' }, 0)
      .to(clockG, { opacity: 1, duration: .4 }, .2)
      .to(student, { opacity: 1, duration: .25 }, .35)
      .to(p, { w: LA / LW, duration: T.wDead - T.wStart, ease: 'none' }, T.wStart)
      .to(deadX, { opacity: 1, scaleY: 1, duration: .25, ease: 'back.out(3)' }, T.wDead - .05)
      .to(deadChip, { opacity: 1, scale: 1, duration: .3, ease: 'back.out(2)' }, T.wDead)
      .to(p, { w: 1, duration: T.wStairs - T.wBack, ease: 'none' }, T.wBack)
      .to(floorChip, { opacity: 1, scale: 1, duration: .3, ease: 'back.out(2)' }, T.wStairs)
      .to(student, { opacity: .45, duration: .3 }, T.wStairs + .1)
      // Beat 2: places don't lead to other places.
      .to(heads[0], { opacity: 0, y: -8, duration: .3 }, T.b2)
      .to(heads[1], { opacity: 1, y: 0, duration: .45, ease: 'power3.out' }, T.b2 + .15)
      .to([floorChip, deadChip], { opacity: 0, duration: .3 }, T.b2)
      .to(trail, { opacity: .3, duration: .4 }, T.b2)
      .to(student, { opacity: .2, duration: .3 }, T.b2)
      .to(looks, { opacity: 1, duration: .3 }, T.b2 + .15)
      .to([hiWing, hiAnnex], { opacity: 1, duration: .3, stagger: .1 }, T.b2 + .3)
      .to(looks, { opacity: 0, duration: .3 }, T.b2 + .75)
      .to(deadX, { opacity: 0, duration: .2 }, T.b2 + .7)
      .fromTo(gap, { opacity: 0, scale: .4, transformOrigin: '50% 50%' }, { opacity: 1, scale: 1, duration: .35, ease: 'back.out(3)', immediateRender: false }, T.b2 + .7)
      .to(gapChip, { opacity: 1, scale: 1, duration: .35, ease: 'back.out(2)' }, T.b2 + .8)
      // Beat 3: a faster way.
      .to(heads[1], { opacity: 0, y: -8, duration: .3 }, T.b3)
      .to(heads[2], { opacity: 1, y: 0, duration: .45, ease: 'power3.out' }, T.b3 + .15)
      .to([gapChip, hiWing, hiAnnex, gap], { opacity: 0, duration: .35 }, T.b3)
      .to(trail, { opacity: .14, duration: .4 }, T.b3)
      .to(student, { opacity: 1, duration: .3 }, T.b3 + .1)
      .fromTo(clockG, { scale: 1, transformOrigin: '100% 50%' }, { scale: 1.12, duration: .15, yoyo: true, repeat: 1, immediateRender: false }, T.b3 - .15)
      .to(p, { r1: 1, duration: T.r2 - T.r1, ease: 'power1.in' }, T.r1)
      .to(elev1, { fill: 'rgba(122,208,224,.55)', duration: .2 }, T.r2 - .1)
      .to(arcLbl, { opacity: 1, duration: .3 }, T.r2)
      .to(elevLbl1, { opacity: 0, duration: .25 }, T.r2)
      .to(p, { r2: 1, duration: T.r3 - T.r2, ease: 'power1.inOut' }, T.r2)
      .to(elev3, { fill: 'rgba(122,208,224,.55)', duration: .2 }, T.r3 - .1)
      .to(p, { r3: 1, duration: T.rEnd - T.r3, ease: 'power1.out' }, T.r3)
      .to(qG, { opacity: 0, scale: .4, duration: .2 }, T.rEnd - .05)
      .to(okG, { opacity: 1, scale: 1, duration: .35, ease: 'back.out(3)' }, T.rEnd)
      .to(dest, { fill: 'rgba(255,182,140,.4)', duration: .25, yoyo: true, repeat: 3 }, T.rEnd)
      // Beat 4: caption, hold.
      .to(cap, { opacity: 1, scale: 1, duration: .5, ease: 'back.out(1.6)' }, T.cap)
      .set({}, {}, T.end);
    render(0);
  }
  build();

  function play() { build(); tl.play(0); }
  function pause() { tl?.pause(); extras.forEach(e => e.pause()); }
  function seek(seconds) { if (!tl) build(); tl.pause(); tl.seek(Math.max(0, Math.min(T.end, seconds)), false); render(tl.time()); }
  function destroy() { tl?.kill(); tl = null; root.innerHTML = ''; }
  if (opts.autoplay) play();
  return { play, pause, destroy, seek, duration: T.end };
}

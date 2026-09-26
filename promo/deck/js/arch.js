// arch.js — "How it works" in three beats: Pick a room -> Walk -> Hands-free.
// Inline SVG, GSAP + MotionPathPlugin globals.
// Contract: mount(el, opts) -> { play(), pause(), destroy(), seek(s) }

const NS = 'http://www.w3.org/2000/svg';
const C = {
  ink: '#1b1614', clay: '#c67c4e', warm: '#ffb68c', teal: '#7ad0e0',
  gold: '#ffd166', err: '#e05a4e', white: '#ffffff',
};
const DIM = 'rgba(255,255,255,0.7)';
const LINE = 'rgba(255,255,255,0.28)';

function E(tag, attrs, parent) {
  const n = document.createElementNS(NS, tag);
  if (attrs) for (const k in attrs) n.setAttribute(k, attrs[k]);
  if (parent) parent.appendChild(n);
  return n;
}
function T(parent, x, y, str, attrs) {
  const t = E('text', Object.assign({ x, y, 'text-anchor': 'middle', fill: C.white,
    'font-family': 'Sora, sans-serif', 'font-weight': 500, 'font-size': 22 }, attrs || {}), parent);
  t.textContent = str;
  return t;
}

// Beat centres (x) and shared rows (y).
const X1 = 245, X2 = 700, X3 = 1150;
const Y_TITLE = 484, Y_CAP = 526, CAP_LH = 30;

export function mount(el, opts = {}) {
  if (window.gsap && window.MotionPathPlugin) gsap.registerPlugin(MotionPathPlugin);

  const svg = E('svg', { viewBox: '0 0 1400 650', width: '100%', height: '100%',
    preserveAspectRatio: 'xMidYMid meet', role: 'img',
    'aria-label': 'Pick a room on the phone, walk with arrows on the floor, then go hands-free with glasses and watch' });
  svg.style.display = 'block';
  svg.style.overflow = 'visible';
  el.appendChild(svg);

  // ---- defs ----
  const defs = E('defs', null, svg);
  const glow = E('filter', { id: 'arch-glow', x: '-50%', y: '-50%', width: '200%', height: '200%' }, defs);
  E('feGaussianBlur', { stdDeviation: 5, result: 'b' }, glow);
  const fm = E('feMerge', null, glow);
  E('feMergeNode', { in: 'b' }, fm);
  E('feMergeNode', { in: 'SourceGraphic' }, fm);
  const gPhone = E('linearGradient', { id: 'arch-phone', x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
  E('stop', { offset: '0', 'stop-color': C.clay, 'stop-opacity': 0.22 }, gPhone);
  E('stop', { offset: '1', 'stop-color': C.clay, 'stop-opacity': 0.05 }, gPhone);
  const gFloor = E('linearGradient', { id: 'arch-floor', x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
  E('stop', { offset: '0', 'stop-color': C.teal, 'stop-opacity': 0.02 }, gFloor);
  E('stop', { offset: '1', 'stop-color': C.teal, 'stop-opacity': 0.14 }, gFloor);
  const gChev = E('linearGradient', { id: 'arch-chev', x1: 0, y1: 1, x2: 0, y2: 0 }, defs);
  E('stop', { offset: '0', 'stop-color': C.clay }, gChev);
  E('stop', { offset: '1', 'stop-color': C.warm }, gChev);

  const Llinks = E('g', null, svg);
  const Lbeats = E('g', null, svg);
  const Ltop = E('g', null, svg);

  // ---- top pill: everything on the phone, no network ----
  const pill = E('g', null, Ltop);
  const pillBg = E('rect', { x: 0, y: 16, width: 10, height: 44, rx: 22,
    fill: 'rgba(255,255,255,0.05)', stroke: 'rgba(255,255,255,0.22)', 'stroke-width': 1.5 }, pill);
  const cloud = E('g', null, pill);
  E('path', {
    d: 'M-15,10 C-24,10 -25,-1 -17,-2 C-17,-11 -7,-14 -2,-7 C1,-15 14,-14 14,-4 C21,-4 22,10 14,10 Z',
    fill: 'none', stroke: 'rgba(255,255,255,0.8)', 'stroke-width': 2.2, 'stroke-linejoin': 'round',
  }, cloud);
  E('line', { x1: -20, y1: -14, x2: 18, y2: 14, stroke: C.err, 'stroke-width': 3, 'stroke-linecap': 'round' }, cloud);
  const pillTx = T(pill, 0, 44, 'everything on the phone · no network',
    { 'font-size': 18, fill: 'rgba(255,255,255,0.85)', 'text-anchor': 'start' });

  function layoutPill() {
    let w = 340;
    try { w = pillTx.getComputedTextLength() || w; } catch (e) { /* not rendered yet */ }
    const total = 28 + 44 + w + 26;   // pad, icon, text, pad
    const x0 = 700 - total / 2;
    pillBg.setAttribute('x', x0);
    pillBg.setAttribute('width', total);
    cloud.setAttribute('transform', `translate(${x0 + 28 + 18},38)`);
    pillTx.setAttribute('x', x0 + 28 + 44);
  }
  layoutPill();
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(layoutPill).catch(() => {});

  // ---- beat scaffolding ----
  function beat(cx, title, capLines) {
    const g = E('g', null, Lbeats);
    const art = E('g', null, g);
    const words = E('g', null, g);
    T(words, cx, Y_TITLE, title, { 'font-size': 40, 'font-weight': 700 });
    const cap = E('g', null, words);
    capLines.forEach((s, i) => T(cap, cx, Y_CAP + i * CAP_LH, s, { fill: DIM }));
    return { g, art, words };
  }

  // ===== Beat 1: Pick a room (phone) =====
  const B1 = beat(X1, 'Pick a room', ['Phone plans the route.', 'Offline.']);
  {
    const a = B1.art;
    const x = X1 - 105, y = 104, w = 210, h = 320;
    E('rect', { x, y, width: w, height: h, rx: 30, fill: 'url(#arch-phone)', stroke: C.clay, 'stroke-width': 3 }, a);
    E('rect', { x: X1 - 22, y: y + 12, width: 44, height: 8, rx: 4, fill: 'rgba(255,255,255,0.25)' }, a);
    // search chip
    E('rect', { x: x + 16, y: y + 34, width: w - 32, height: 46, rx: 23,
      fill: 'rgba(27,22,20,0.8)', stroke: 'rgba(255,255,255,0.3)', 'stroke-width': 1.5 }, a);
    E('circle', { cx: x + 40, cy: y + 55, r: 7, fill: 'none', stroke: C.warm, 'stroke-width': 2.5 }, a);
    E('line', { x1: x + 45, y1: y + 60, x2: x + 51, y2: y + 66, stroke: C.warm, 'stroke-width': 2.5, 'stroke-linecap': 'round' }, a);
    T(a, x + 62, y + 64, 'Room 3361', { 'font-size': 20, 'font-weight': 700, 'text-anchor': 'start' });
    // mini route on a floor
    const mapY = y + 98;
    E('rect', { x: x + 16, y: mapY, width: w - 32, height: 116, rx: 14,
      fill: 'rgba(255,255,255,0.03)', stroke: 'rgba(255,255,255,0.12)', 'stroke-width': 1 }, a);
    const route = E('path', { d: `M${x + 42},${mapY + 96} L${x + 42},${mapY + 58} L${x + 118},${mapY + 58} L${x + 118},${mapY + 26} L${x + 162},${mapY + 26}`,
      fill: 'none', stroke: C.warm, 'stroke-width': 5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, a);
    B1.route = route;
    E('circle', { cx: x + 42, cy: mapY + 96, r: 7, fill: C.teal }, a);
    // destination pin
    E('path', { d: `M${x + 172},${mapY + 30} c-10,-12 -10,-26 0,-26 c10,0 10,14 0,26 z`, fill: C.gold,
      transform: `translate(0,-4)` }, a);
    E('circle', { cx: x + 172, cy: mapY + 13, r: 3.5, fill: C.ink }, a);
    // route card
    const cy = y + 230;
    E('rect', { x: x + 16, y: cy, width: w - 32, height: 70, rx: 16, fill: C.clay }, a);
    const t = T(a, X1, cy + 44, '', { 'font-size': 26, 'font-weight': 700, fill: C.ink });
    const t1 = E('tspan', null, t); t1.textContent = '1:27';
    const t2 = E('tspan', { 'font-size': 19, 'font-weight': 500 }, t); t2.textContent = ' · elevator';
  }

  // ===== Beat 2: Walk (arrows on the floor through the camera) =====
  const B2 = beat(X2, 'Walk', ['Arrows on the floor', 'through the phone camera.']);
  const chevs = [];
  {
    const a = B2.art;
    const vx0 = X2 - 175, vx1 = X2 + 175, vy0 = 104, vy1 = 424;
    // floor plane
    const hz = 200;            // horizon-ish top edge
    const topL = X2 - 70, topR = X2 + 70, botL = vx0 + 6, botR = vx1 - 6, bot = vy1 - 6;
    E('path', { d: `M${topL},${hz} L${topR},${hz} L${botR},${bot} L${botL},${bot} Z`, fill: 'url(#arch-floor)' }, a);
    const grid = E('g', { stroke: 'rgba(122,208,224,0.28)', 'stroke-width': 1.2 }, a);
    for (let i = 0; i <= 6; i++) {
      const f = i / 6;
      E('line', { x1: topL + (topR - topL) * f, y1: hz, x2: botL + (botR - botL) * f, y2: bot }, grid);
    }
    for (const f of [0, 0.12, 0.28, 0.5, 0.78]) {
      const yy = hz + (bot - hz) * f;
      const l = topL + (botL - topL) * f, r = topR + (botR - topR) * f;
      E('line', { x1: l, y1: yy, x2: r, y2: yy }, grid);
    }
    // chevrons, near -> far
    function chev(cx, yTop, w, h, th) {
      const k = th * (w / 2) / h;
      const d = `M${cx - w / 2},${yTop + h} L${cx},${yTop} L${cx + w / 2},${yTop + h} ` +
        `L${cx + w / 2 - k * 1.6},${yTop + h} L${cx},${yTop + th} L${cx - w / 2 + k * 1.6},${yTop + h} Z`;
      return E('path', { d, fill: 'url(#arch-chev)', filter: 'url(#arch-glow)' }, a);
    }
    chevs.push(chev(X2, 318, 190, 64, 30));
    chevs.push(chev(X2, 262, 120, 38, 18));
    chevs.push(chev(X2, 226, 76, 22, 11));
    // viewfinder corners
    const vf = E('g', { fill: 'none', stroke: 'rgba(255,255,255,0.75)', 'stroke-width': 3.5, 'stroke-linecap': 'round' }, a);
    const L = 30;
    E('path', { d: `M${vx0},${vy0 + L} L${vx0},${vy0} L${vx0 + L},${vy0}` }, vf);
    E('path', { d: `M${vx1 - L},${vy0} L${vx1},${vy0} L${vx1},${vy0 + L}` }, vf);
    E('path', { d: `M${vx0},${vy1 - L} L${vx0},${vy1} L${vx0 + L},${vy1}` }, vf);
    E('path', { d: `M${vx1 - L},${vy1} L${vx1},${vy1} L${vx1},${vy1 - L}` }, vf);
    // camera badge
    const cam = E('g', { transform: `translate(${X2},${vy0 + 40})` }, a);
    E('circle', { r: 28, fill: C.ink, stroke: C.teal, 'stroke-width': 2.5 }, cam);
    E('rect', { x: -15, y: -9, width: 30, height: 21, rx: 5, fill: 'none', stroke: C.teal, 'stroke-width': 2.5 }, cam);
    E('path', { d: 'M-6,-9 L-3,-14 L3,-14 L6,-9', fill: 'none', stroke: C.teal, 'stroke-width': 2.5, 'stroke-linejoin': 'round' }, cam);
    E('circle', { cx: 0, cy: 1.5, r: 5.5, fill: 'none', stroke: C.teal, 'stroke-width': 2.5 }, cam);
  }

  // ===== Beat 3: Hands-free (glasses + watch) =====
  const B3 = beat(X3, 'Hands-free', ['Glasses speak, the watch buzzes.', 'You never look down.']);
  const rings = [];
  let watchChev = null, waves = null;
  {
    const a = B3.art;
    const gx = X3 - 85, gy = 300;     // glasses centre
    const wx = X3 + 115, wy = 300;    // watch centre
    // speech bubble above glasses
    const bx = gx - 110, by = 120, bw = 250, bh = 92;
    E('path', { d: `M${bx + 18},${by} L${bx + bw - 18},${by} Q${bx + bw},${by} ${bx + bw},${by + 18} L${bx + bw},${by + bh - 18} ` +
      `Q${bx + bw},${by + bh} ${bx + bw - 18},${by + bh} L${gx + 18},${by + bh} L${gx - 4},${by + bh + 24} L${gx - 6},${by + bh} ` +
      `L${bx + 18},${by + bh} Q${bx},${by + bh} ${bx},${by + bh - 18} L${bx},${by + 18} Q${bx},${by} ${bx + 18},${by} Z`,
    fill: 'rgba(122,208,224,0.14)', stroke: C.teal, 'stroke-width': 2.5, 'stroke-linejoin': 'round' }, a);
    T(a, bx + bw / 2, by + 40, 'Turn left at', { 'font-size': 23, 'font-weight': 700 });
    T(a, bx + bw / 2, by + 70, 'the elevator', { 'font-size': 23, 'font-weight': 700 });
    // glasses
    const gl = E('g', { fill: 'rgba(255,255,255,0.06)', stroke: C.white, 'stroke-width': 4.5,
      'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, a);
    E('path', { d: `M${gx - 78},${gy - 8} C${gx - 78},${gy - 26} ${gx - 70},${gy - 30} ${gx - 52},${gy - 30} L${gx - 26},${gy - 30} C${gx - 10},${gy - 30} ${gx - 8},${gy - 20} ${gx - 12},${gy} C${gx - 16},${gy + 20} ${gx - 30},${gy + 30} ${gx - 48},${gy + 30} C${gx - 68},${gy + 30} ${gx - 78},${gy + 16} ${gx - 78},${gy - 8} Z` }, gl);
    E('path', { d: `M${gx + 78},${gy - 8} C${gx + 78},${gy - 26} ${gx + 70},${gy - 30} ${gx + 52},${gy - 30} L${gx + 26},${gy - 30} C${gx + 10},${gy - 30} ${gx + 8},${gy - 20} ${gx + 12},${gy} C${gx + 16},${gy + 20} ${gx + 30},${gy + 30} ${gx + 48},${gy + 30} C${gx + 68},${gy + 30} ${gx + 78},${gy + 16} ${gx + 78},${gy - 8} Z` }, gl);
    E('path', { d: `M${gx - 12},${gy - 16} C${gx - 6},${gy - 26} ${gx + 6},${gy - 26} ${gx + 12},${gy - 16}`, fill: 'none' }, gl);
    E('path', { d: `M${gx - 78},${gy - 18} L${gx - 96},${gy - 24}`, fill: 'none' }, gl);
    E('path', { d: `M${gx + 78},${gy - 18} L${gx + 96},${gy - 24}`, fill: 'none' }, gl);
    // sound waves from the temple
    waves = E('g', { fill: 'none', stroke: C.teal, 'stroke-width': 3, 'stroke-linecap': 'round' }, a);
    [0, 1, 2].forEach((k) => {
      const r = 12 + k * 10, ox = gx - 100, oy = gy + 6;
      E('path', { d: `M${ox - r * 0.5},${oy - r * 0.87} A${r},${r} 0 0 0 ${ox - r * 0.5},${oy + r * 0.87}`, opacity: 0.9 - k * 0.2 }, waves);
    });
    // watch
    for (let i = 0; i < 2; i++) rings.push(E('circle', { cx: wx, cy: wy, r: 58, fill: 'none', stroke: C.gold,
      'stroke-width': 3, opacity: 0 }, a));
    E('rect', { x: wx - 24, y: wy - 92, width: 48, height: 36, rx: 8, fill: 'rgba(255,255,255,0.16)' }, a);
    E('rect', { x: wx - 24, y: wy + 56, width: 48, height: 36, rx: 8, fill: 'rgba(255,255,255,0.16)' }, a);
    E('circle', { cx: wx, cy: wy, r: 58, fill: '#241d1a', stroke: 'rgba(255,255,255,0.45)', 'stroke-width': 4 }, a);
    E('circle', { cx: wx, cy: wy, r: 46, fill: C.ink, stroke: 'rgba(255,255,255,0.85)', 'stroke-width': 2.5 }, a);
    // left-pointing chevron
    watchChev = E('path', { d: `M${wx + 8},${wy - 20} L${wx - 14},${wy} L${wx + 8},${wy + 20}`, fill: 'none',
      stroke: C.gold, 'stroke-width': 9, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, a);
  }

  // ---- connectors between beats ----
  function link(x0, x1, y) {
    const p = E('path', { d: `M${x0},${y} L${x1},${y}`, fill: 'none', stroke: C.clay, 'stroke-width': 3,
      'stroke-linecap': 'round' }, Llinks);
    const len = x1 - x0;
    p.style.strokeDasharray = `${len}`;
    p.style.strokeDashoffset = '0';
    const head = E('path', { d: `M${x1 - 10},${y - 9} L${x1},${y} L${x1 - 10},${y + 9}`, fill: 'none', stroke: C.clay,
      'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, Llinks);
    const pk = E('circle', { cx: 0, cy: 0, r: 7, fill: C.warm, filter: 'url(#arch-glow)', opacity: 0 }, Llinks);
    return { p, len, head, pk };
  }
  const K1 = link(X1 + 128, X2 - 196, 264);
  const K2 = link(X2 + 196, X3 - 206, 264);

  // ---- animation ----
  let ctx = null, master = null;

  function reveal(tl, B, at) {
    tl.from(B.art, { opacity: 0, scale: 0.86, y: 16, transformOrigin: '50% 50%', duration: 0.6, ease: 'back.out(1.6)' }, at);
    tl.from(B.words.children, { opacity: 0, y: 14, duration: 0.5, stagger: 0.12, ease: 'power2.out' }, at + 0.2);
  }
  function drawLink(tl, K, at) {
    tl.fromTo(K.p, { strokeDashoffset: K.len }, { strokeDashoffset: 0, duration: 0.5, ease: 'power2.inOut' }, at);
    tl.from(K.head, { opacity: 0, duration: 0.2 }, at + 0.4);
  }
  function packetLoop(K, delay) {
    const t = gsap.timeline({ repeat: -1, delay });
    t.set(K.pk, { opacity: 1 }, 0);
    t.fromTo(K.pk, { motionPath: { path: K.p, align: K.p, alignOrigin: [0.5, 0.5], start: 0, end: 0 } },
      { motionPath: { path: K.p, align: K.p, alignOrigin: [0.5, 0.5], start: 0, end: 1 }, duration: 1.1, ease: 'power1.inOut' }, 0);
    t.to(K.pk, { opacity: 0, duration: 0.15 }, 1.0);
    t.set({}, {}, 2.4);
    return t;
  }

  function build() {
    master = gsap.timeline({ paused: true });
    const tl = master;
    tl.from(pill, { opacity: 0, y: -10, duration: 0.5, ease: 'power2.out' }, 0);
    reveal(tl, B1, 0.2);
    tl.fromTo(B1.route, { strokeDasharray: 300, strokeDashoffset: 300 }, { strokeDashoffset: 0, duration: 0.6, ease: 'power2.out' }, 0.55);
    drawLink(tl, K1, 0.6);
    reveal(tl, B2, 1.0);
    tl.from(chevs, { opacity: 0, y: 12, duration: 0.35, stagger: 0.1, ease: 'power2.out' }, 1.25);
    drawLink(tl, K2, 1.4);
    reveal(tl, B3, 1.8);

    const L0 = 2.7; // subtle loops start once all three beats have landed

    tl.add(packetLoop(K1, 0), L0);
    tl.add(packetLoop(K2, 1.2), L0);

    // slow chevron pulse, near -> far (walking forward)
    const cp = gsap.timeline({ repeat: -1 });
    cp.to(chevs, { opacity: 0.45, duration: 0.5, stagger: 0.25, ease: 'sine.inOut' }, 0);
    cp.to(chevs, { opacity: 1, duration: 0.6, stagger: 0.25, ease: 'sine.inOut' }, 0.55);
    cp.set({}, {}, 2.4);
    tl.add(cp, L0);

    // watch: chevron nudge + two haptic rings; glasses: sound waves
    const wt = gsap.timeline({ repeat: -1 });
    wt.fromTo(watchChev, { x: 0 }, { x: -6, duration: 0.18, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 0);
    rings.forEach((r, i) => {
      wt.fromTo(r, { attr: { r: 58 }, opacity: 0.85 }, { attr: { r: 98 }, opacity: 0, duration: 1.0, ease: 'power2.out' }, i * 0.22);
    });
    wt.fromTo(waves.children, { opacity: 0.15 }, { opacity: 0.95, duration: 0.3, stagger: 0.12, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 0.9);
    wt.set({}, {}, 2.4);
    tl.add(wt, L0 + 0.3);
  }

  function play() {
    if (!window.gsap) return;
    if (ctx) ctx.revert();
    layoutPill();
    ctx = gsap.context(build, svg);
    master.play(0);
  }
  function pause() { if (master) master.pause(); }
  function destroy() {
    if (ctx) ctx.revert();
    ctx = null; master = null;
    svg.remove();
  }
  // seek(seconds): jump to a frame and hold it (paused). Handy for posters and tests.
  function seek(s) {
    if (!window.gsap) return;
    if (!master) play();
    master.pause();
    master.seek(s);
  }
  if (opts.autoplay) play();
  return { play, pause, destroy, seek };
}

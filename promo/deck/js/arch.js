// arch.js — "Three devices, one route engine" (inline SVG, GSAP + MotionPathPlugin globals).
// Contract: mount(el, opts) -> { play(), pause(), destroy() }

const NS = 'http://www.w3.org/2000/svg';
const C = {
  ink: '#1b1614', clay: '#c67c4e', warm: '#ffb68c', teal: '#7ad0e0',
  gold: '#ffd166', err: '#e05a4e', white: '#ffffff',
};

function E(tag, attrs, parent) {
  const n = document.createElementNS(NS, tag);
  if (attrs) for (const k in attrs) n.setAttribute(k, attrs[k]);
  if (parent) parent.appendChild(n);
  return n;
}
function T(parent, x, y, str, attrs) {
  const t = E('text', Object.assign({ x, y, 'text-anchor': 'middle', fill: C.white,
    'font-family': 'Sora, sans-serif', 'font-weight': 500, 'font-size': 14 }, attrs || {}), parent);
  t.textContent = str;
  return t;
}

export function mount(el, opts = {}) {
  if (window.gsap && window.MotionPathPlugin) gsap.registerPlugin(MotionPathPlugin);

  const svg = E('svg', { viewBox: '0 0 1400 650', width: '100%', height: '100%',
    preserveAspectRatio: 'xMidYMid meet', role: 'img',
    'aria-label': 'Glasses, phone and watch; the phone runs the route engine on-device' });
  svg.style.display = 'block';
  svg.style.overflow = 'visible';
  el.appendChild(svg);

  // ---- defs ----
  const defs = E('defs', null, svg);
  const glow = E('filter', { id: 'arch-glow', x: '-50%', y: '-50%', width: '200%', height: '200%' }, defs);
  E('feGaussianBlur', { stdDeviation: 6, result: 'b' }, glow);
  const fm = E('feMerge', null, glow);
  E('feMergeNode', { in: 'b' }, fm);
  E('feMergeNode', { in: 'SourceGraphic' }, fm);
  const soft = E('filter', { id: 'arch-soft', x: '-50%', y: '-50%', width: '200%', height: '200%' }, defs);
  E('feGaussianBlur', { stdDeviation: 14 }, soft);
  const g1 = E('linearGradient', { id: 'arch-phone', x1: 0, y1: 0, x2: 1, y2: 1 }, defs);
  E('stop', { offset: '0', 'stop-color': C.clay, 'stop-opacity': 0.26 }, g1);
  E('stop', { offset: '1', 'stop-color': C.clay, 'stop-opacity': 0.06 }, g1);

  // ---- layers ----
  const Lcloud = E('g', null, svg);
  const Llinks = E('g', null, svg);
  const Lcards = E('g', null, svg);
  const Lpackets = E('g', null, svg);

  // ---- cloud (struck through) ----
  const cloud = E('g', null, Lcloud);
  E('path', {
    d: 'M652,98 C630,98 626,72 646,68 C644,48 670,40 684,54 C692,34 728,34 736,56 C754,50 772,64 764,80 C782,84 778,100 760,100 Z',
    fill: 'rgba(255,255,255,0.04)', stroke: 'rgba(255,255,255,0.45)', 'stroke-width': 2,
    'stroke-linejoin': 'round',
  }, cloud);
  const strike = E('line', { x1: 628, y1: 30, x2: 782, y2: 116, stroke: C.err, 'stroke-width': 4,
    'stroke-linecap': 'round' }, cloud);
  T(cloud, 700, 142, 'no network indoors', { fill: 'rgba(255,255,255,0.62)', 'font-size': 15 });

  // ---- cards ----
  function card(x, y, w, h, hot) {
    const g = E('g', null, Lcards);
    E('rect', { x, y, width: w, height: h, rx: 24,
      fill: hot ? 'url(#arch-phone)' : 'rgba(255,255,255,0.045)',
      stroke: hot ? C.clay : 'rgba(255,255,255,0.16)', 'stroke-width': hot ? 2 : 1.5 }, g);
    return g;
  }

  // Glasses (left)
  const gCard = card(60, 215, 280, 230);
  const glassesArt = E('g', { fill: 'none', stroke: C.white, 'stroke-width': 3, 'stroke-linecap': 'round',
    'stroke-linejoin': 'round' }, gCard);
  E('path', { d: 'M122,286 C122,272 128,268 142,268 L170,268 C184,268 190,274 188,290 C186,308 176,318 156,318 C136,318 122,306 122,286 Z' }, glassesArt);
  E('path', { d: 'M278,286 C278,272 272,268 258,268 L230,268 C216,268 210,274 212,290 C214,308 224,318 244,318 C264,318 278,306 278,286 Z' }, glassesArt);
  E('path', { d: 'M188,280 C194,272 206,272 212,280' }, glassesArt);
  E('path', { d: 'M122,276 L100,270' }, glassesArt);
  E('path', { d: 'M278,276 L300,270' }, glassesArt);
  const camDot = E('circle', { cx: 132, cy: 262, r: 4.5, fill: 'rgba(255,255,255,0.25)' }, gCard);
  T(gCard, 200, 356, 'Glasses', { 'font-weight': 700, 'font-size': 20 });
  T(gCard, 200, 378, 'Ray-Ban Meta Gen 2', { fill: 'rgba(255,255,255,0.6)', 'font-size': 13 });
  function indicator(cx, label) {
    const g = E('g', null, gCard);
    const bg = E('rect', { x: cx - 34, y: 396, width: 68, height: 26, rx: 13,
      fill: 'rgba(255,255,255,0.05)', stroke: 'rgba(255,255,255,0.18)', 'stroke-width': 1 }, g);
    const dot = E('circle', { cx: cx - 18, cy: 409, r: 4.5, fill: 'rgba(255,255,255,0.22)' }, g);
    const tx = T(g, cx + 8, 414, label, { 'font-weight': 700, 'font-size': 12,
      fill: 'rgba(255,255,255,0.45)', 'letter-spacing': 1 });
    return { g, bg, dot, tx };
  }
  const CAM = indicator(162, 'CAM');
  const MIC = indicator(238, 'MIC');
  const overlapNote = T(svg, 200, 474, 'camera and audio never overlap', {
    fill: 'rgba(255,255,255,0.45)', 'font-size': 13 });
  Lcards.appendChild(overlapNote);

  // Phone (centre)
  const pulse = E('rect', { x: 530, y: 180, width: 340, height: 300, rx: 26, fill: 'none',
    stroke: C.clay, 'stroke-width': 3, opacity: 0 }, Lcards);
  const pCard = card(530, 180, 340, 300, true);
  // tiny phone glyph
  E('rect', { x: 554, y: 202, width: 20, height: 34, rx: 5, fill: 'none', stroke: C.warm, 'stroke-width': 2 }, pCard);
  E('line', { x1: 561, y1: 231, x2: 567, y2: 231, stroke: C.warm, 'stroke-width': 2, 'stroke-linecap': 'round' }, pCard);
  T(pCard, 712, 224, 'Phone · route engine', { 'font-weight': 700, 'font-size': 23 });
  T(pCard, 712, 247, 'Galaxy S25 Ultra', { fill: 'rgba(255,255,255,0.6)', 'font-size': 13 });
  const chips = [['Dijkstra core', 552, 272], ['anchors', 704, 272], ['OCR', 552, 326], ['TTS', 704, 326]];
  let coreChip = null;
  for (const [label, x, y] of chips) {
    const g = E('g', null, pCard);
    const r = E('rect', { x, y, width: 144, height: 44, rx: 13,
      fill: 'rgba(27,22,20,0.55)', stroke: 'rgba(255,182,140,0.35)', 'stroke-width': 1.2 }, g);
    T(g, x + 72, y + 27, label, { 'font-size': 15, 'font-weight': label === 'Dijkstra core' ? 700 : 500,
      fill: label === 'Dijkstra core' ? C.warm : C.white });
    if (!coreChip) coreChip = r;
  }
  const onDev = E('g', null, pCard);
  E('rect', { x: 620, y: 396, width: 160, height: 34, rx: 17, fill: 'rgba(122,208,224,0.12)',
    stroke: C.teal, 'stroke-width': 1.3 }, onDev);
  E('path', { d: 'M640,405 L648,408 L648,414 C648,419 644,422 640,424 C636,422 632,419 632,414 L632,408 Z',
    fill: 'none', stroke: C.teal, 'stroke-width': 1.8, 'stroke-linejoin': 'round' }, onDev);
  T(onDev, 712, 418, 'all on-device', { fill: C.teal, 'font-size': 14, 'font-weight': 700 });
  T(pCard, 700, 460, 'building JSON · anchors · routing, offline', { fill: 'rgba(255,255,255,0.42)', 'font-size': 12 });

  // Watch (right)
  const wCard = card(1060, 215, 280, 230);
  const rings = [0, 1].map(() => E('circle', { cx: 1200, cy: 298, r: 54, fill: 'none', stroke: C.gold,
    'stroke-width': 2.5, opacity: 0 }, wCard));
  E('rect', { x: 1180, y: 228, width: 40, height: 22, rx: 6, fill: 'rgba(255,255,255,0.12)' }, wCard);
  E('rect', { x: 1180, y: 346, width: 40, height: 22, rx: 6, fill: 'rgba(255,255,255,0.12)' }, wCard);
  E('circle', { cx: 1200, cy: 298, r: 54, fill: '#241d1a', stroke: 'rgba(255,255,255,0.35)', 'stroke-width': 3 }, wCard);
  const ticks = E('g', { stroke: 'rgba(255,255,255,0.35)', 'stroke-width': 1.5 }, wCard);
  for (let i = 0; i < 24; i++) {
    const a = (i / 24) * Math.PI * 2;
    E('line', { x1: 1200 + Math.cos(a) * 47, y1: 298 + Math.sin(a) * 47,
      x2: 1200 + Math.cos(a) * 51, y2: 298 + Math.sin(a) * 51 }, ticks);
  }
  E('circle', { cx: 1200, cy: 298, r: 42, fill: C.ink, stroke: 'rgba(255,255,255,0.8)', 'stroke-width': 2 }, wCard);
  const chevron = E('path', { d: 'M1183,310 L1200,286 L1217,310', fill: 'none', stroke: C.gold,
    'stroke-width': 7, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, wCard);
  T(wCard, 1200, 396, 'Watch', { 'font-weight': 700, 'font-size': 20 });
  T(wCard, 1200, 418, 'Galaxy Watch 8 Classic', { fill: 'rgba(255,255,255,0.6)', 'font-size': 13 });

  // User (below watch)
  const user = E('g', null, Lcards);
  E('circle', { cx: 1200, cy: 574, r: 10, fill: 'none', stroke: C.white, 'stroke-width': 2.5 }, user);
  E('path', { d: 'M1180,608 C1180,592 1190,586 1200,586 C1210,586 1220,592 1220,608', fill: 'none',
    stroke: C.white, 'stroke-width': 2.5, 'stroke-linecap': 'round' }, user);
  T(user, 1200, 632, 'you', { fill: 'rgba(255,255,255,0.6)', 'font-size': 13 });
  const buzz = E('g', { fill: 'none', stroke: C.gold, 'stroke-width': 2.2, 'stroke-linecap': 'round' }, user);
  const buzzArcs = [];
  for (const s of [-1, 1]) for (const k of [0, 1, 2]) {
    const x = 1200 + s * (30 + k * 9);
    buzzArcs.push(E('path', { d: `M${x},${578 - k * 4} Q${x + s * (8 + k * 2)},588 ${x},${598 + k * 4}`,
      opacity: 0.25 }, buzz));
  }
  T(user, 1268, 594, 'buzz', { fill: C.gold, 'font-size': 14, 'font-weight': 700, 'text-anchor': 'start' });

  // ---- links ----
  function link(d, color) {
    const p = E('path', { d, fill: 'none', stroke: color, 'stroke-width': 2.4, 'stroke-linecap': 'round',
      opacity: 0.75 }, Llinks);
    const len = p.getTotalLength();
    p.style.strokeDasharray = `${len}`;
    p.style.strokeDashoffset = '0';
    return { p, len };
  }
  const L1 = link('M342,288 C420,236 462,236 528,272', C.warm);   // glasses -> phone
  const L2 = link('M528,392 C462,436 420,436 342,380', C.teal);   // phone -> glasses
  const L3 = link('M872,330 C944,288 990,288 1058,322', C.gold);  // phone -> watch
  const L4 = link('M1200,447 C1200,490 1200,520 1200,556', C.gold); // watch -> user
  const linkLabels = E('g', null, Llinks);
  function label2(x, y, a, b, color) {
    const g = E('g', null, linkLabels);
    T(g, x, y, a, { fill: color, 'font-weight': 700, 'font-size': 15 });
    T(g, x, y + 19, b, { fill: 'rgba(255,255,255,0.66)', 'font-size': 13 });
    return g;
  }
  label2(435, 200, 'camera burst', '2–3 stills / ~2 s', C.warm);
  label2(435, 470, 'speech', '“Turn left at the elevator”', C.teal);
  label2(965, 250, 'RouteStep', '< 1 s', C.gold);
  const L4lab = T(linkLabels, 1214, 508, 'arrow + haptic', { fill: 'rgba(255,255,255,0.55)', 'font-size': 12,
    'text-anchor': 'start' });

  // ---- packets ----
  function pill(text, fill, w) {
    const g = E('g', { opacity: 0 }, Lpackets);
    E('rect', { x: -w / 2, y: -9, width: w, height: 18, rx: 9, fill, filter: 'url(#arch-glow)' }, g);
    T(g, 0, 4, text, { fill: C.ink, 'font-size': 10, 'font-weight': 700, 'letter-spacing': 0.5 });
    return g;
  }
  const imgPk = [0, 1, 2].map(() => pill('IMG', C.warm, 38));
  const ttsPk = pill('TTS', C.teal, 38);
  const stepPk = pill('STEP', C.gold, 44);
  const tapPk = E('circle', { r: 6, fill: C.gold, opacity: 0, filter: 'url(#arch-glow)' }, Lpackets);

  // ---- animation ----
  let ctx = null, master = null;
  const cardsInOrder = [gCard, pCard, wCard];

  function along(tl, node, path, dur, at) {
    tl.set(node, { opacity: 1 }, at);
    tl.fromTo(node, { motionPath: { path, align: path, alignOrigin: [0.5, 0.5], start: 0, end: 0 } },
      { motionPath: { path, align: path, alignOrigin: [0.5, 0.5], start: 0, end: 1 }, duration: dur, ease: 'power1.inOut' }, at);
    tl.to(node, { opacity: 0, duration: 0.12 }, at + dur - 0.06);
  }
  function setInd(ind, on, color) {
    return {
      fill: on ? color : 'rgba(255,255,255,0.22)',
    };
  }

  function build() {
    master = gsap.timeline({ paused: true });
    const tl = master;
    // intro
    tl.from(cloud, { opacity: 0, scale: 0.7, transformOrigin: '50% 50%', duration: 0.5, ease: 'back.out(1.7)' }, 0);
    tl.fromTo(strike, { attr: { x2: 628, y2: 30 } }, { attr: { x2: 782, y2: 116 }, duration: 0.35, ease: 'power2.out' }, 0.35);
    tl.from(cardsInOrder, { opacity: 0, scale: 0.72, transformOrigin: '50% 50%', duration: 0.7,
      ease: 'back.out(1.7)', stagger: 0.14 }, 0.15);
    tl.from([user, overlapNote], { opacity: 0, y: 12, duration: 0.5, ease: 'power2.out' }, 0.7);
    [L1, L2, L3, L4].forEach((L, i) => {
      tl.fromTo(L.p, { strokeDashoffset: L.len }, { strokeDashoffset: 0, duration: 0.7, ease: 'power2.inOut' }, 0.75 + i * 0.16);
    });
    tl.from(linkLabels.children, { opacity: 0, y: 8, duration: 0.45, stagger: 0.1, ease: 'power2.out' }, 1.1);

    const L0 = 1.9; // loops start

    // phone core: soft pulse
    const core = gsap.timeline({ repeat: -1 });
    core.fromTo(pulse, { opacity: 0.55, scale: 1, transformOrigin: '50% 50%' },
      { opacity: 0, scale: 1.07, duration: 1.8, ease: 'sine.out' }, 0);
    core.fromTo(coreChip, { attr: { stroke: 'rgba(255,182,140,0.35)' } },
      { attr: { stroke: 'rgba(255,182,140,0.95)' }, duration: 0.9, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 0);
    tl.add(core, L0);

    // glasses: burst -> stop -> speak. CAM and MIC never lit together.
    const gl = gsap.timeline({ repeat: -1 });
    const P = 3.4;
    gl.set([CAM.dot], setInd(CAM, true, C.warm), 0);
    gl.set(CAM.bg, { attr: { stroke: C.warm, fill: 'rgba(255,182,140,0.16)' } }, 0);
    gl.set(CAM.tx, { attr: { fill: C.warm } }, 0);
    for (let i = 0; i < 3; i++) {
      gl.fromTo(camDot, { attr: { fill: '#ffffff', r: 6 } }, { attr: { fill: 'rgba(255,255,255,0.25)', r: 4.5 }, duration: 0.3 }, 0.05 + i * 0.32);
      along(gl, imgPk[i], L1.p, 0.9, 0.05 + i * 0.32);
    }
    gl.set(CAM.dot, setInd(CAM, false), 1.3);
    gl.set(CAM.bg, { attr: { stroke: 'rgba(255,255,255,0.18)', fill: 'rgba(255,255,255,0.05)' } }, 1.3);
    gl.set(CAM.tx, { attr: { fill: 'rgba(255,255,255,0.45)' } }, 1.3);
    // phone "thinks" as the frames land
    gl.fromTo(coreChip, { attr: { fill: 'rgba(198,124,78,0.55)' } }, { attr: { fill: 'rgba(27,22,20,0.55)' }, duration: 0.6 }, 1.55);
    along(gl, ttsPk, L2.p, 0.9, 1.6);
    gl.set(MIC.dot, setInd(MIC, true, C.teal), 2.45);
    gl.set(MIC.bg, { attr: { stroke: C.teal, fill: 'rgba(122,208,224,0.16)' } }, 2.45);
    gl.set(MIC.tx, { attr: { fill: C.teal } }, 2.45);
    gl.set(MIC.dot, setInd(MIC, false), 3.2);
    gl.set(MIC.bg, { attr: { stroke: 'rgba(255,255,255,0.18)', fill: 'rgba(255,255,255,0.05)' } }, 3.2);
    gl.set(MIC.tx, { attr: { fill: 'rgba(255,255,255,0.45)' } }, 3.2);
    gl.set({}, {}, P);
    tl.add(gl, L0);

    // watch: RouteStep every 1.6 s -> chevron flash + haptic rings + buzz
    const wt = gsap.timeline({ repeat: -1 });
    along(wt, stepPk, L3.p, 0.7, 0);
    wt.fromTo(chevron, { scale: 1.25, transformOrigin: '50% 50%' }, { scale: 1, duration: 0.45, ease: 'back.out(3)' }, 0.68);
    rings.forEach((r, i) => {
      wt.fromTo(r, { attr: { r: 54 }, opacity: 0.8 }, { attr: { r: 96 }, opacity: 0, duration: 0.9, ease: 'power2.out' }, 0.7 + i * 0.18);
    });
    along(wt, tapPk, L4.p, 0.35, 0.72);
    wt.fromTo(buzzArcs, { opacity: 1 }, { opacity: 0.25, duration: 0.5, stagger: 0.03, ease: 'power1.in' }, 1.05);
    wt.fromTo(buzz, { x: -2 }, { x: 2, duration: 0.05, repeat: 5, yoyo: true, ease: 'none' }, 1.05);
    wt.set({}, {}, 1.6);
    tl.add(wt, L0 + 0.4);
  }

  function play() {
    if (!window.gsap) return;
    if (ctx) ctx.revert();
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

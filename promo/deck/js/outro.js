// outro.js - 10 s closing card for the CampusMaps demo video: the whole system at a glance.
// 5:4 (1800x1440), transparent. Centre: Classroom South as a turning 3D floor stack (floors 1, 2, 6)
// with the E-WM -> elevator -> R-608 route drawn in clay. Around it: six technology chips wired to the
// building, a seventh strip at the bottom, and the title on top.
// Contract: mount(el, opts) -> { play(), pause(), destroy(), seek(s), duration }
// Virtual-clock safe: ONE finite paused gsap timeline drives everything, including the three.js camera
// (angle = f(timeline time)); no setTimeout, no Date, no CSS animation, no infinite repeats, no randomness.
// The building JSON is read synchronously in mount, so the first frame after play() is already complete.
// Beats: 0-2.2 s floors rise and the route draws; 0.3 s title; 2.0-5.6 s chips 1..6 then the strip;
// 6.5-9.0 s hold, gold ring pulse, a bead runs the route; 9.0-10.0 s fade to fully transparent.

import * as THREE from 'three';

const W = 1800, H = 1440, DURATION = 10;
const NS = 'http://www.w3.org/2000/svg';
const C = { ink: '#1b1614', white: '#fff8f4', clay: '#c67c4e', warm: '#ffb68c', teal: '#7ad0e0', gold: '#ffd166' };
const DIM = 'rgba(255,248,244,0.78)';
const PANEL = 'rgba(27,22,20,0.62)';

const ROUTE = ['E-WM', 'H7', 'H1', 'H2', 'H9', 'EL-1', 'EL-2', 'EL-6', 'H6', 'H11', 'R-608'];
const FLOORS = [1, 2, 6];
const GAP = 17;            // m between displayed floors (evenly spaced, not to scale)
const PAD = 4;             // slab margin around the nodes, m
const LIFT = 0.5;          // route height above the slab, m

// Canvas box on the 1800x1440 stage (the building lives here)
const CV = { x: W / 2 - 540, y: 310, w: 1080, h: 900 };
const BC = { x: CV.x + CV.w / 2, y: CV.y + CV.h / 2 };   // building centre on stage

// Chips: odd numbers on the left, even on the right, so the reveal alternates sides.
const CHIP_W = 480, CHIP_H = 164, CHIP_GAP = 42, CHIP_Y0 = 438;
const CHIPS = [
  { tag: 'CORE', name: 'Route engine', sub: ['Dijkstra, pure Kotlin,', 'time-aware doors, 45 tests'] },
  { tag: 'PHONE AR', name: 'ARCore + SceneView', sub: ['World-locked arrows,', 'Jetpack Compose'] },
  { tag: 'OUTDOOR', name: 'ARCore Geospatial', sub: ['VPS to the door, ~1 m'] },
  { tag: 'GLASSES', name: 'Meta Wearables Toolkit', sub: ['Ray-Ban Meta: burst,', 'then speak'] },
  { tag: 'INDOOR FIX', name: 'ML Kit + Augmented Images', sub: ['Sign OCR and poster anchors'] },
  { tag: 'WATCH', name: 'Wear OS Data Layer', sub: ['Galaxy Watch, haptics under 1 s'] },
];
const STRIP = 'ElevenLabs voice  ·  barometer floors  ·  offline';

function E(tag, attrs, parent) {
  const n = document.createElementNS(NS, tag);
  if (attrs) for (const k in attrs) n.setAttribute(k, attrs[k]);
  if (parent) parent.appendChild(n);
  return n;
}
function T(parent, x, y, str, attrs) {
  const t = E('text', Object.assign({ x, y, fill: C.white, 'font-family': 'Sora, sans-serif',
    'font-weight': 500, 'font-size': 24 }, attrs || {}), parent);
  t.textContent = str;
  return t;
}
function fit(t, maxW) {
  let w = 0;
  try { w = t.getComputedTextLength(); } catch (e) { w = 0; }
  if (w > maxW) t.setAttribute('font-size', (parseFloat(t.getAttribute('font-size')) * maxW / w).toFixed(1));
}

function loadJSON(url) {
  // Synchronous on purpose: the capture driver's virtual clock must not race an async fetch.
  try {
    const x = new XMLHttpRequest();
    x.open('GET', url, false);
    x.send(null);
    if (x.status === 200 || x.status === 0) return JSON.parse(x.responseText);
  } catch (e) { console.error('outro: cannot load', url, e); }
  return null;
}

function glowTexture() {
  const c = document.createElement('canvas');
  c.width = c.height = 128;
  const g = c.getContext('2d');
  const grd = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  grd.addColorStop(0, 'rgba(255,244,232,1)');
  grd.addColorStop(0.22, 'rgba(255,182,140,0.9)');
  grd.addColorStop(0.55, 'rgba(198,124,78,0.3)');
  grd.addColorStop(1, 'rgba(198,124,78,0)');
  g.fillStyle = grd;
  g.fillRect(0, 0, 128, 128);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

export function mount(el, opts = {}) {
  const gsap = window.gsap;
  if (getComputedStyle(el).position === 'static') el.style.position = 'relative';

  // ---------------------------------------------------------------- frame (1800x1440, scaled to fit)
  const frame = document.createElement('div');
  Object.assign(frame.style, { position: 'absolute', left: '0', top: '0', width: W + 'px', height: H + 'px',
    transformOrigin: '0 0', pointerEvents: 'none' });
  el.appendChild(frame);
  function fitFrame() {
    const ew = el.clientWidth || W, eh = el.clientHeight || H;
    const s = Math.min(ew / W, eh / H);
    frame.style.transform = `translate(${(ew - W * s) / 2}px, ${(eh - H * s) / 2}px) scale(${s})`;
  }
  fitFrame();
  const ro = new ResizeObserver(fitFrame);
  ro.observe(el);

  // ---------------------------------------------------------------- three.js building
  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(1);
  renderer.setSize(CV.w, CV.h, false);
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  const canvas = renderer.domElement;
  Object.assign(canvas.style, { position: 'absolute', left: CV.x + 'px', top: CV.y + 'px',
    width: CV.w + 'px', height: CV.h + 'px', display: 'block' });
  frame.appendChild(canvas);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, CV.w / CV.h, 1, 3000);
  const disposables = [];
  const track = (o) => { disposables.push(o); return o; };

  const data = loadJSON(opts.dataUrl || './data/CS.json');
  const S = { r0: 0, r1: 0, r2: 0, route: 0, bead: 0, ring: 0 };   // animated state, read in render()
  const floorMats = [[], [], []];   // [material, baseOpacity] per displayed floor
  const floorGroups = [];
  let routePath = null, routeGeo = null, routeSegs = 0, headSprite = null, destSprite = null, beadSprite = null, ringMesh = null;
  const TUBE_RADIAL = 8, TUBE_SEGS = 480;

  if (data) {
    const nodes = data.nodes.filter(n => FLOORS.includes(n.floor));
    const byId = {}; nodes.forEach(n => { byId[n.id] = n; });
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    nodes.forEach(n => { minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x); minY = Math.min(minY, n.y); maxY = Math.max(maxY, n.y); });
    const cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
    const level = f => FLOORS.indexOf(f);
    const P = (n, lift = 0) => new THREE.Vector3(n.x - cx, level(n.floor) * GAP + lift, -(n.y - cy));
    const sw = maxX - minX + 2 * PAD, sd = maxY - minY + 2 * PAD;

    const nodeGeo = track(new THREE.SphereGeometry(0.85, 16, 12));
    const slabGeo = track(new THREE.BoxGeometry(sw, 0.35, sd));
    const edgesGeo = track(new THREE.EdgesGeometry(slabGeo));
    const colorOf = t => t === 'room' ? 0xffb68c : t === 'entrance' ? 0xfff8f4
      : (t === 'elevator' || t === 'stairs') ? 0x7ad0e0 : 0xc9bfb8;

    FLOORS.forEach((f, i) => {
      const g = new THREE.Group();
      g.userData.y = i * GAP;
      scene.add(g);
      floorGroups.push(g);
      const reg = (m, base) => { floorMats[i].push([m, base]); m.transparent = true; m.opacity = 0; return track(m); };

      const slab = new THREE.Mesh(slabGeo, reg(new THREE.MeshBasicMaterial({ color: 0xe8d9cc, depthWrite: false, side: THREE.DoubleSide }), 0.08));
      slab.position.y = -0.3;
      slab.renderOrder = i;
      g.add(slab);
      const outline = new THREE.LineSegments(edgesGeo, reg(new THREE.LineBasicMaterial({ color: 0xc67c4e }), 0.75));
      outline.position.y = -0.3;
      g.add(outline);

      // same-floor edges
      const pts = [];
      for (const e of data.edges) {
        const a = byId[e.from], b = byId[e.to];
        if (!a || !b || a.floor !== f || b.floor !== f) continue;
        const pa = P(a, 0.2), pb = P(b, 0.2);
        pa.y -= i * GAP; pb.y -= i * GAP;
        pts.push(pa, pb);
      }
      if (pts.length) {
        const lg = track(new THREE.BufferGeometry().setFromPoints(pts));
        g.add(new THREE.LineSegments(lg, reg(new THREE.LineBasicMaterial({ color: 0xfff8f4, depthWrite: false }), 0.42)));
      }
      // nodes
      const mats = {};
      nodes.filter(n => n.floor === f).forEach(n => {
        const col = colorOf(n.type);
        if (!mats[col]) mats[col] = reg(new THREE.MeshBasicMaterial({ color: col }), 0.95);
        const m = new THREE.Mesh(nodeGeo, mats[col]);
        const p = P(n, 0.6); p.y -= i * GAP;
        m.position.copy(p);
        if (n.type === 'intersection' || n.type === 'waypoint') m.scale.setScalar(0.6);
        g.add(m);
      });
    });

    // vertical shafts (elevator and stairs edges), faint teal, fade in with the upper floors
    const vpts = [];
    for (const e of data.edges) {
      if (e.kind !== 'elevator' && e.kind !== 'stairs') continue;
      const a = byId[e.from], b = byId[e.to];
      if (!a || !b) continue;
      vpts.push(P(a, 0.6), P(b, 0.6));
    }
    if (vpts.length) {
      const vm = track(new THREE.LineBasicMaterial({ color: 0x7ad0e0, transparent: true, opacity: 0, depthWrite: false }));
      floorMats[2].push([vm, 0.4]);
      scene.add(new THREE.LineSegments(track(new THREE.BufferGeometry().setFromPoints(vpts)), vm));
    }

    // route
    const rn = ROUTE.map(id => byId[id]).filter(Boolean);
    if (rn.length >= 2) {
      routePath = new THREE.CurvePath();
      for (let i = 1; i < rn.length; i++) routePath.add(new THREE.LineCurve3(P(rn[i - 1], LIFT + 0.6), P(rn[i], LIFT + 0.6)));
      routeGeo = track(new THREE.TubeGeometry(routePath, TUBE_SEGS, 0.62, TUBE_RADIAL, false));
      routeSegs = TUBE_SEGS;
      routeGeo.setDrawRange(0, 0);
      const tube = new THREE.Mesh(routeGeo, track(new THREE.MeshBasicMaterial({ color: 0xc67c4e })));
      tube.renderOrder = 10;
      scene.add(tube);
      const tex = track(glowTexture());
      const mk = (s) => {
        const sp = new THREE.Sprite(track(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false, depthTest: false, opacity: 0 })));
        sp.scale.setScalar(s);
        sp.renderOrder = 20;
        scene.add(sp);
        return sp;
      };
      headSprite = mk(7);
      beadSprite = mk(6);
      destSprite = mk(11);
      destSprite.position.copy(P(rn[rn.length - 1], LIFT + 0.8));
    }

    // gold ring around the base, pulses once in the hold
    ringMesh = new THREE.Mesh(track(new THREE.RingGeometry(1, 1.014, 160)),
      track(new THREE.MeshBasicMaterial({ color: 0xffd166, transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false })));
    ringMesh.rotation.x = -Math.PI / 2;
    ringMesh.position.y = -1.2;
    ringMesh.renderOrder = 5;
    scene.add(ringMesh);
  }
  const R0 = 40;   // ring base radius, m (about the slab half-diagonal)

  let tNow = 0;
  function render() {
    const t = tNow;
    for (let i = 0; i < floorGroups.length; i++) {
      const r = S['r' + i];
      floorGroups[i].position.y = floorGroups[i].userData.y - (1 - r) * 22;
      for (const [m, base] of floorMats[i]) m.opacity = base * r;
    }
    if (routeGeo) {
      const p = Math.max(0, Math.min(1, S.route));
      routeGeo.setDrawRange(0, Math.floor(p * routeSegs) * TUBE_RADIAL * 6);
      const on = p > 0.001 && p < 0.999;
      headSprite.material.opacity = on ? 1 : 0;
      if (on) headSprite.position.copy(routePath.getPointAt(p));
      destSprite.material.opacity = Math.max(0, Math.min(1, (p - 0.9) * 10));
      const b = S.bead;
      beadSprite.material.opacity = b > 0 && b < 1 ? Math.min(1, b * 8, (1 - b) * 8) : 0;
      if (b > 0 && b < 1) beadSprite.position.copy(routePath.getPointAt(b));
    }
    if (ringMesh) {
      const k = S.ring;
      ringMesh.scale.setScalar(R0 * (0.96 + 0.2 * k));
      ringMesh.material.opacity = k > 0 && k < 1 ? Math.sin(Math.PI * k) * 0.95 : 0;
    }
    // slow continuous orbit with a gentle tilt, a slight dolly-in during the intro
    const az = -0.95 + 0.24 * t;
    const elv = 0.5 + 0.07 * Math.sin(t * 0.55);
    const dist = 252 * (1 + 0.12 * Math.pow(1 - Math.min(1, t / 2.6), 2));
    const cy = GAP * 1.0 - 1;
    camera.position.set(dist * Math.cos(elv) * Math.sin(az), cy + dist * Math.sin(elv), dist * Math.cos(elv) * Math.cos(az));
    camera.lookAt(0, cy, 0);
    renderer.render(scene, camera);
  }

  // ---------------------------------------------------------------- SVG overlay
  const svg = E('svg', { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: 'img',
    'aria-label': 'CampusMaps: native Android, one Kotlin core, three devices, and the technologies behind it' });
  Object.assign(svg.style, { position: 'absolute', left: '0', top: '0', overflow: 'visible' });
  frame.appendChild(svg);
  const defs = E('defs', null, svg);
  const sh = E('filter', { id: 'ou-shadow', filterUnits: 'userSpaceOnUse', x: -100, y: -100, width: W + 200, height: H + 200 }, defs);
  E('feDropShadow', { dx: 0, dy: 3, stdDeviation: 6, 'flood-color': '#000', 'flood-opacity': 0.5 }, sh);
  const shSoft = E('filter', { id: 'ou-soft', filterUnits: 'userSpaceOnUse', x: -100, y: -100, width: W + 200, height: H + 200 }, defs);
  E('feDropShadow', { dx: 0, dy: 2, stdDeviation: 3, 'flood-color': '#000', 'flood-opacity': 0.45 }, shSoft);

  // title
  const gHead = E('g', { filter: 'url(#ou-shadow)' }, svg);
  const kick = T(gHead, W / 2, 138, 'CAMPUSMAPS', { 'text-anchor': 'middle', fill: C.clay, 'font-weight': 700,
    'font-size': 26, 'letter-spacing': 10 });
  const title1 = T(gHead, W / 2, 232, 'Native Android. One Kotlin core.', { 'text-anchor': 'middle', 'font-weight': 800, 'font-size': 78 });
  const title2 = T(gHead, W / 2, 322, 'Three devices.', { 'text-anchor': 'middle', 'font-weight': 800, 'font-size': 78, fill: C.warm });

  // connectors (under the chips)
  const gLinks = E('g', null, svg);
  const gChips = E('g', null, svg);
  const chipEls = [];
  CHIPS.forEach((c, i) => {
    const left = i % 2 === 0;
    const row = Math.floor(i / 2);
    const x = left ? 90 : W - 90 - CHIP_W;
    const y = CHIP_Y0 + row * (CHIP_H + CHIP_GAP);
    const cyy = y + CHIP_H / 2;
    // connector: chip inner edge -> a point at the rim of the building
    const x0 = left ? x + CHIP_W : x;
    const ex = left ? BC.x - 225 : BC.x + 225;
    const ey = BC.y + (row - 1) * 150;
    const mx = (x0 + ex) / 2;
    const d = `M${x0},${cyy} C${mx},${cyy} ${mx},${ey} ${ex},${ey}`;
    const path = E('path', { d, fill: 'none', stroke: C.clay, 'stroke-width': 2.5, 'stroke-linecap': 'round', opacity: 0.85 }, gLinks);
    const len = path.getTotalLength ? path.getTotalLength() || 400 : 400;
    path.setAttribute('stroke-dasharray', `${len} ${len}`);
    path.setAttribute('stroke-dashoffset', len);
    const dot = E('circle', { cx: ex, cy: ey, r: 7, fill: C.ink, stroke: C.warm, 'stroke-width': 3, opacity: 0 }, gLinks);
    const port = E('circle', { cx: x0, cy: cyy, r: 5, fill: C.clay, opacity: 0 }, gLinks);

    const g = E('g', { opacity: 0 }, gChips);
    E('rect', { x, y, width: CHIP_W, height: CHIP_H, rx: 24, fill: PANEL, stroke: 'rgba(198,124,78,0.55)', 'stroke-width': 2 }, g);
    E('rect', { x: left ? x + 22 : x + 22, y: y + 30, width: 5, height: CHIP_H - 60, rx: 2.5, fill: C.clay }, g);
    const tx = x + 50;
    const gt = E('g', { filter: 'url(#ou-soft)' }, g);
    T(gt, tx, y + 44, c.tag, { fill: C.teal, 'font-weight': 700, 'font-size': 17, 'letter-spacing': 3.5 });
    const nm = T(gt, tx, y + 84, c.name, { 'font-weight': 700, 'font-size': 31 });
    const subs = c.sub.map((s, k) => T(gt, tx, y + 118 + k * 28, s, { fill: DIM, 'font-weight': 400, 'font-size': 21 }));
    chipEls.push({ g, path, len, dot, port, nm, subs, left });
  });

  // seventh strip
  const STRIP_W = 820, STRIP_H = 76, STRIP_Y = 1194;
  const gStrip = E('g', { opacity: 0 }, svg);
  E('rect', { x: (W - STRIP_W) / 2, y: STRIP_Y, width: STRIP_W, height: STRIP_H, rx: STRIP_H / 2, fill: PANEL,
    stroke: 'rgba(255,209,102,0.55)', 'stroke-width': 2 }, gStrip);
  const stripT = T(E('g', { filter: 'url(#ou-soft)' }, gStrip), W / 2, STRIP_Y + 48, STRIP, { 'text-anchor': 'middle',
    'font-weight': 500, 'font-size': 26, fill: C.white });
  const stripLink = E('path', { d: `M${W / 2},${STRIP_Y} L${W / 2},${BC.y + 330}`, stroke: C.gold, 'stroke-width': 2.5,
    'stroke-linecap': 'round', opacity: 0.8, 'stroke-dasharray': '120 120', 'stroke-dashoffset': 120 }, svg);
  svg.insertBefore(stripLink, gLinks);

  function layoutText() {
    fit(title1, W - 2 * 120);
    chipEls.forEach(c => { fit(c.nm, CHIP_W - 50 - 26); c.subs.forEach(s => fit(s, CHIP_W - 50 - 26)); });
    fit(stripT, STRIP_W - 60);
  }
  layoutText();
  if (document.fonts && document.fonts.load) {
    Promise.all(['400', '500', '700', '800'].map(w => document.fonts.load(`${w} 30px Sora`)))
      .then(layoutText).catch(() => {});
  }

  // ---------------------------------------------------------------- timeline
  let master = null;
  const ctx = gsap.context(() => {
    gsap.set([kick, title1, title2], { opacity: 0, y: 34 });
    gsap.set(canvas, { opacity: 1 });
    const tl = gsap.timeline({ paused: true, onUpdate: () => { tNow = tl.time(); render(); } });
    master = tl;

    // 0-2.2 s: floors rise one after another, the route draws through them
    FLOORS.forEach((f, i) => {
      tl.to(S, { ['r' + i]: 1, duration: 1.15, ease: 'power3.out' }, 0.05 + i * 0.28);
    });
    tl.to(S, { route: 1, duration: 1.5, ease: 'power1.inOut' }, 0.75);

    // 0.3 s: title
    tl.to(kick, { opacity: 1, y: 0, duration: 0.6, ease: 'power3.out' }, 0.3);
    tl.to(title1, { opacity: 1, y: 0, duration: 0.7, ease: 'power3.out' }, 0.38);
    tl.to(title2, { opacity: 1, y: 0, duration: 0.7, ease: 'power3.out' }, 0.5);

    // 2.0-5.6 s: chips, alternating sides, each wired to the building
    chipEls.forEach((c, i) => {
      const t0 = 2.0 + i * 0.62;
      gsap.set(c.g, { x: c.left ? -26 : 26 });
      tl.to(c.g, { opacity: 1, x: 0, duration: 0.55, ease: 'power3.out' }, t0);
      tl.to(c.port, { opacity: 1, duration: 0.2 }, t0 + 0.2);
      tl.to(c.path, { attr: { 'stroke-dashoffset': 0 }, duration: 0.5, ease: 'power2.inOut' }, t0 + 0.25);
      tl.fromTo(c.dot, { opacity: 0, scale: 0.3, transformOrigin: '50% 50%' },
        { opacity: 1, scale: 1, duration: 0.35, ease: 'back.out(2.5)', immediateRender: false }, t0 + 0.68);
    });
    gsap.set(gStrip, { y: 20 });
    tl.to(gStrip, { opacity: 1, y: 0, duration: 0.6, ease: 'power3.out' }, 5.55);
    tl.to(stripLink, { attr: { 'stroke-dashoffset': 0 }, duration: 0.45, ease: 'power2.out' }, 5.8);

    // 6.5-9.0 s: hold; a bead runs the route, the gold ring pulses once
    tl.to(S, { bead: 1, duration: 2.0, ease: 'power1.inOut' }, 6.6);
    tl.fromTo(S, { ring: 0 }, { ring: 1, duration: 1.6, ease: 'sine.inOut', immediateRender: false }, 6.9);

    // 9.0-10.0 s: fade everything to fully transparent
    tl.to([svg, canvas], { opacity: 0, duration: 1.0, ease: 'power1.in' }, 9.0);
    tl.set({}, {}, DURATION);
  }, svg);

  render();

  function play() { if (master) master.restart(); }
  function pause() { if (master) master.pause(); }
  function seek(s) { if (!master) return; master.pause(); master.seek(s); tNow = master.time(); render(); }
  function destroy() {
    if (master) master.kill();
    ctx.revert();
    ro.disconnect();
    disposables.forEach(d => { try { d.dispose(); } catch (e) { /* ignore */ } });
    renderer.dispose();
    frame.remove();
  }
  if (opts.autoplay) play();
  return { play, pause, destroy, seek, duration: DURATION };
}

// building3d.js - exploded 3D floor stack with an animated route (CampusMaps deck).
// Contract: mount(el, opts) -> { play(), pause(), destroy() }
// opts: { code: 'KL' | 'CS', route?: string[] (node ids), autoplay?: boolean (default true) }
// Needs the importmap for `three` / `three/addons/` and the global `gsap`.

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';

const COLORS = {
  clay: 0xc67c4e,
  room: 0xffb68c,
  entrance: 0xffffff,
  vertical: 0x7ad0e0,
  other: 0x8a8480,
  anchor: 0xffd166,
  slab: 0xe8d9cc,
};

const DEFAULT_ROUTES = {
  KL: ['E-RWD', 'H1', 'EL-1', 'EL-3', 'H3', 'R-3361'],
  CS: ['E-WM', 'H7', 'H1', 'H2', 'H9', 'EL-1', 'EL-2', 'EL-6', 'H6', 'H11', 'R-608'],
};

const FOV = 28;
const ROUTE_LIFT = 0.6;      // route tube height above the floor, m
const SLAB_PAD = 3;          // per side, so 6 m total per axis
const SLAB_THICK = 0.3;
const FIT_ELEVATION = 0.38;  // rad, final 3/4 view (about 22 deg)

let styleInjected = false;
function injectStyle() {
  if (styleInjected) return;
  styleInjected = true;
  const s = document.createElement('style');
  s.textContent = `
.b3d-label{font-family:'Sora',system-ui,sans-serif;font-size:13px;color:rgba(255,255,255,.8);
  white-space:nowrap;pointer-events:none;text-shadow:0 1px 3px rgba(0,0,0,.9),0 0 8px rgba(27,22,20,.9);
  transform:translateY(-14px);opacity:0;letter-spacing:.01em}
.b3d-label.room{color:#ffb68c}
.b3d-label.dest{color:#fff;font-weight:600}
.b3d-floor{font-family:'Sora',system-ui,sans-serif;font-size:12px;font-weight:600;color:rgba(198,124,78,.9);
  letter-spacing:.12em;text-transform:uppercase;pointer-events:none;opacity:0}`;
  document.head.appendChild(s);
}

function glowTexture() {
  const c = document.createElement('canvas');
  c.width = c.height = 128;
  const g = c.getContext('2d');
  const grd = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  grd.addColorStop(0, 'rgba(255,240,225,1)');
  grd.addColorStop(0.25, 'rgba(255,182,140,.85)');
  grd.addColorStop(0.6, 'rgba(198,124,78,.25)');
  grd.addColorStop(1, 'rgba(198,124,78,0)');
  g.fillStyle = grd;
  g.fillRect(0, 0, 128, 128);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

function shortName(n) {
  if (n.type === 'room') return n.name || n.id;
  if (n.type === 'elevator' || n.type === 'stairs') return (n.name || n.id).split(',')[0];
  return n.name || n.id;
}

export function mount(el, opts = {}) {
  const code = opts.code || 'KL';
  const autoplay = opts.autoplay !== false;
  const gsap = window.gsap;
  injectStyle();

  if (getComputedStyle(el).position === 'static') el.style.position = 'relative';
  el.style.overflow = 'hidden';

  // ---------- renderer, scene, camera ----------
  const renderer = new THREE.WebGLRenderer({
    antialias: true, alpha: true, preserveDrawingBuffer: !!opts.preserveDrawingBuffer, // true only for screenshots
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  Object.assign(renderer.domElement.style, { position: 'absolute', inset: '0', display: 'block' });
  el.appendChild(renderer.domElement);

  const labelRenderer = new CSS2DRenderer();
  Object.assign(labelRenderer.domElement.style, { position: 'absolute', inset: '0', pointerEvents: 'none' });
  el.appendChild(labelRenderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(FOV, 2, 0.5, 2000);
  scene.add(new THREE.HemisphereLight(0xfff1e6, 0x2a211d, 1.6));
  const sun = new THREE.DirectionalLight(0xffffff, 1.4);
  sun.position.set(40, 80, 30);
  scene.add(sun);

  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.enableZoom = false;
  controls.enablePan = false;
  controls.autoRotateSpeed = opts.autoRotateSpeed ?? 0.4;
  controls.enabled = false;

  // ---------- state ----------
  let rafId = 0;
  let running = false;
  let destroyed = false;
  let wantPlay = autoplay;
  let built = null;               // everything created from data
  let introTl = null;
  let routeTl = null;
  let pulseTl = null;
  let phase = 'idle';             // idle | intro | route | done
  const disposables = [];
  const labelDivs = [];
  const floorLabelDivs = [];
  const track = (o) => { disposables.push(o); return o; };

  const cam = { az: 0, el: 0, dist: 1 };
  let center = new THREE.Vector3();
  let fit = { az: Math.PI / 4, el: FIT_ELEVATION, dist: 100 };

  function applyCam() {
    const ce = Math.cos(cam.el);
    camera.position.set(
      center.x + cam.dist * ce * Math.sin(cam.az),
      center.y + cam.dist * Math.sin(cam.el),
      center.z + cam.dist * ce * Math.cos(cam.az),
    );
    camera.lookAt(center);
  }

  // ---------- size ----------
  let lastW = -1, lastH = -1;
  function resize() {
    const w = Math.max(1, el.clientWidth);
    const h = Math.max(1, el.clientHeight);
    lastW = el.clientWidth; lastH = el.clientHeight;
    renderer.setSize(w, h);
    labelRenderer.setSize(w, h);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    if (built) computeFit();
    if (!running) renderOnce();
    // intro was waiting for a real size (slide was hidden / 0-wide)
    if (built && phase === 'idle' && wantPlay && el.clientWidth > 0 && el.clientHeight > 0) play();
  }
  const ro = new ResizeObserver(() => resize());
  ro.observe(el);

  function renderOnce() {
    if (destroyed) return;
    renderer.render(scene, camera);
    labelRenderer.render(scene, camera);
  }

  function loop() {
    if (!running) return;
    rafId = requestAnimationFrame(loop);
    // fallback for containers that were display:none / 0-wide when observed
    if (el.clientWidth !== lastW || el.clientHeight !== lastH) resize();
    if (controls.enabled) controls.update();
    renderOnce();
  }
  function startLoop() {
    if (running || destroyed) return;
    running = true;
    rafId = requestAnimationFrame(loop);
  }
  function stopLoop() {
    running = false;
    cancelAnimationFrame(rafId);
  }

  // ---------- build from data ----------
  // Distance and target that fit every slab corner in view for the final azimuth/elevation
  // (iterates on the real projection so near corners are not cropped by perspective).
  const fitCam = new THREE.PerspectiveCamera(FOV, 1, 0.1, 1e6);
  function computeFit() {
    const pts = built.fitPts;
    fitCam.aspect = camera.aspect;
    fitCam.updateProjectionMatrix();
    const ce = Math.cos(fit.el);
    const back = new THREE.Vector3(ce * Math.sin(fit.az), Math.sin(fit.el), ce * Math.cos(fit.az));
    const right = new THREE.Vector3(0, 1, 0).cross(back).normalize();
    const up = back.clone().cross(right).normalize();
    const tv = Math.tan(THREE.MathUtils.degToRad(FOV) / 2);
    const margin = 0.9;                                // use 90% of the frame
    center = built.bounds.getCenter(new THREE.Vector3());
    let dist = built.bounds.getSize(new THREE.Vector3()).length() / (2 * tv);
    const v = new THREE.Vector3();
    for (let i = 0; i < 12; i++) {
      fitCam.position.copy(center).addScaledVector(back, dist);
      fitCam.lookAt(center);
      fitCam.updateMatrixWorld();
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
      for (const q of pts) {
        v.copy(q).project(fitCam);
        minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
        minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
      }
      // shift the target toward the middle of the projected extents (NDC -> world at target depth)
      const cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
      center.addScaledVector(right, cx * tv * camera.aspect * dist).addScaledVector(up, cy * tv * dist);
      const extent = Math.max((maxX - minX) / 2, (maxY - minY) / 2) / margin;
      dist *= 0.5 + 0.5 * extent;                      // damped zoom toward extent == 1
    }
    fit.dist = dist;
    camera.far = Math.max(2000, fit.dist * 5);
    camera.updateProjectionMatrix();
    if (phase === 'done' || phase === 'route') controls.target.copy(center);
  }

  function build(data) {
    const nodes = new Map(data.nodes.map((n) => [n.id, n]));
    const floors = [...new Set(data.nodes.map((n) => n.floor))].sort((a, b) => a - b);
    const minFloor = floors[0];
    // floorSpacing 'true': y = (floor - minFloor) * floorHeightM (real scale).
    // default 'even': floors that exist are spaced evenly with an exploded gap sized to
    // the footprint, so a 1/2/6 stack does not leave floor 6 far away and tiny.
    let floorY;
    if (opts.floorSpacing === 'true') {
      const H = data.floorHeightM || 4.7;
      floorY = (f) => (f - minFloor) * H;
    } else {
      const xs = data.nodes.map((n) => n.x), ys = data.nodes.map((n) => n.y);
      const diag = Math.hypot(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys));
      const gap = opts.floorGapM ?? Math.max((data.floorHeightM || 4.7) * 2.5, diag * 0.32);
      floorY = (f) => floors.indexOf(f) * gap;
    }
    const fitPts = [];
    const route = expandRoute(opts.route && opts.route.length ? opts.route : (DEFAULT_ROUTES[code] || []), data, nodes, floorY);
    const routeIds = new Set(route.map((n) => n.id));
    const local = (n, lift = 0) => new THREE.Vector3(n.x, lift, -n.y);
    const world = (n, lift = 0) => new THREE.Vector3(n.x, floorY(n.floor) + lift, -n.y);

    const root = new THREE.Group();
    scene.add(root);
    const bounds = new THREE.Box3();

    const nodeGeo = track(new THREE.SphereGeometry(0.6, 16, 12));
    const mats = {};
    const matFor = (hex) => mats[hex] || (mats[hex] = track(new THREE.MeshStandardMaterial({
      color: hex, emissive: hex, emissiveIntensity: 0.35, roughness: 0.5, metalness: 0,
    })));
    const typeColor = (t) => t === 'room' ? COLORS.room : t === 'entrance' ? COLORS.entrance
      : (t === 'elevator' || t === 'stairs') ? COLORS.vertical : COLORS.other;

    const slabMat = track(new THREE.MeshStandardMaterial({
      color: COLORS.slab, transparent: true, opacity: 0.14, depthWrite: false, roughness: 0.9,
      side: THREE.DoubleSide,
    }));
    const outlineMat = track(new THREE.LineBasicMaterial({ color: COLORS.clay, transparent: true, opacity: 0.45 }));
    const edgeMat = track(new THREE.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.35, depthWrite: false }));
    const outdoorMat = track(new THREE.LineDashedMaterial({ color: 0xffffff, transparent: true, opacity: 0.3, dashSize: 0.8, gapSize: 0.6, depthWrite: false }));
    const anchorGeo = track(new THREE.OctahedronGeometry(0.55, 0));
    const anchorMat = track(new THREE.MeshStandardMaterial({ color: COLORS.anchor, emissive: COLORS.anchor, emissiveIntensity: 0.6 }));
    const ringGeo = track(new THREE.RingGeometry(0.75, 0.95, 24));
    const ringMat = track(new THREE.MeshBasicMaterial({ color: COLORS.anchor, transparent: true, opacity: 0.7, side: THREE.DoubleSide, depthWrite: false }));

    const floorGroups = new Map();
    floors.forEach((f, idx) => {
      const g = new THREE.Group();
      g.userData.targetY = floorY(f);
      g.position.y = 0;
      root.add(g);
      floorGroups.set(f, g);

      const fn = data.nodes.filter((n) => n.floor === f);
      let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
      fn.forEach((n) => {
        minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
        minZ = Math.min(minZ, -n.y); maxZ = Math.max(maxZ, -n.y);
      });
      minX -= SLAB_PAD; maxX += SLAB_PAD; minZ -= SLAB_PAD; maxZ += SLAB_PAD;
      const w = maxX - minX, d = maxZ - minZ;
      const slabGeo = track(new THREE.BoxGeometry(w, SLAB_THICK, d));
      const slab = new THREE.Mesh(slabGeo, slabMat);
      slab.position.set((minX + maxX) / 2, -SLAB_THICK / 2 - 0.05, (minZ + maxZ) / 2);
      slab.renderOrder = idx;
      g.add(slab);
      const outline = new THREE.LineSegments(track(new THREE.EdgesGeometry(slabGeo)), outlineMat);
      outline.position.copy(slab.position);
      outline.renderOrder = idx;
      g.add(outline);

      bounds.expandByPoint(new THREE.Vector3(minX, floorY(f), minZ));
      bounds.expandByPoint(new THREE.Vector3(maxX, floorY(f) + 1, maxZ));
      for (const x of [minX, maxX]) for (const z of [minZ, maxZ]) for (const y of [-0.4, 2.5]) {
        fitPts.push(new THREE.Vector3(x, floorY(f) + y, z));
      }

      // floor tag at the south-west corner
      const tagDiv = document.createElement('div');
      tagDiv.className = 'b3d-floor';
      tagDiv.textContent = 'Floor ' + f;
      floorLabelDivs.push(tagDiv);
      const tag = new CSS2DObject(tagDiv);
      tag.position.set(maxX, 0.2, maxZ);
      tag.center.set(1, 0);
      g.userData.center = new THREE.Vector3((minX + maxX) / 2, 0, (minZ + maxZ) / 2);
      g.add(tag);

      // nodes
      fn.forEach((n) => {
        const m = new THREE.Mesh(nodeGeo, matFor(typeColor(n.type)));
        m.position.copy(local(n, 0.6));
        if (n.type === 'intersection' || n.type === 'waypoint') m.scale.setScalar(0.6);
        m.userData.node = n;
        g.add(m);
        // entrances and rooms always get a label; elevators and stairs only when on the route
        if (n.type === 'entrance' || n.type === 'room'
          || ((n.type === 'elevator' || n.type === 'stairs') && routeIds.has(n.id))) {
          const div = document.createElement('div');
          div.className = 'b3d-label ' + n.type;
          div.textContent = shortName(n);
          labelDivs.push(div);
          const lbl = new CSS2DObject(div);
          lbl.position.copy(local(n, 1.1));
          lbl.userData.nodeId = n.id;
          g.add(lbl);
        }
      });

      // same-floor edges
      const pos = [];
      const outdoorPos = [];
      data.edges.forEach((e) => {
        const a = nodes.get(e.from), b = nodes.get(e.to);
        if (!a || !b || a.floor !== f || b.floor !== f) return;
        const arr = e.kind === 'outdoor' ? outdoorPos : pos;
        arr.push(a.x, 0.08, -a.y, b.x, 0.08, -b.y);
      });
      if (pos.length) {
        const geo = track(new THREE.BufferGeometry());
        geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
        const ls = new THREE.LineSegments(geo, edgeMat);
        ls.renderOrder = floors.length + idx;
        g.add(ls);
      }
      if (outdoorPos.length) {
        const geo = track(new THREE.BufferGeometry());
        geo.setAttribute('position', new THREE.Float32BufferAttribute(outdoorPos, 3));
        const ls = new THREE.LineSegments(geo, outdoorMat);
        ls.computeLineDistances();
        g.add(ls);
      }

      // anchors
      (data.anchors || []).filter((a) => a.floor === f).forEach((a) => {
        const lift = 1.6;
        const dia = new THREE.Mesh(anchorGeo, anchorMat);
        dia.position.set(a.x, lift, -a.y);
        dia.scale.set(0.8, 1.2, 0.8);
        g.add(dia);
        const ring = new THREE.Mesh(ringGeo, ringMat);
        ring.rotation.x = -Math.PI / 2;
        ring.position.set(a.x, 0.1, -a.y);
        g.add(ring);
        const stemGeo = track(new THREE.BufferGeometry().setFromPoints([
          new THREE.Vector3(a.x, 0.1, -a.y), new THREE.Vector3(a.x, lift - 0.6, -a.y)]));
        g.add(new THREE.Line(stemGeo, ringMat));
      });
    });

    // vertical edges: dashed lines in world space, faded in after the explode
    const vertMat = track(new THREE.LineDashedMaterial({
      color: COLORS.vertical, transparent: true, opacity: 0, dashSize: 0.5, gapSize: 0.4, depthWrite: false,
    }));
    const vpos = [];
    data.edges.forEach((e) => {
      const a = nodes.get(e.from), b = nodes.get(e.to);
      if (!a || !b || a.floor === b.floor) return;
      vpos.push(a.x, floorY(a.floor) + 0.1, -a.y, b.x, floorY(b.floor) + 0.1, -b.y);
    });
    let vertLines = null;
    if (vpos.length) {
      const geo = track(new THREE.BufferGeometry());
      geo.setAttribute('position', new THREE.Float32BufferAttribute(vpos, 3));
      vertLines = new THREE.LineSegments(geo, vertMat);
      vertLines.computeLineDistances();
      root.add(vertLines);
    }

    // ---------- route ----------
    let routeObj = null;
    if (route.length >= 2) {
      const pts = [];
      for (let i = 0; i < route.length; i++) {
        const n = route[i];
        const p = world(n, ROUTE_LIFT);
        if (i > 0) {
          const prev = route[i - 1];
          if (prev.floor !== n.floor) {
            const a = world(prev, ROUTE_LIFT);
            // straight shaft: intermediate points on the vertical leg
            for (const t of [0.12, 0.5, 0.88]) {
              pts.push(new THREE.Vector3(
                THREE.MathUtils.lerp(a.x, p.x, t), THREE.MathUtils.lerp(a.y, p.y, t), THREE.MathUtils.lerp(a.z, p.z, t)));
            }
          } else {
            // keep horizontal legs straight too: points just after and before each corner
            const a = world(prev, ROUTE_LIFT);
            const len = a.distanceTo(p);
            if (len > 4) {
              pts.push(a.clone().lerp(p, 1.5 / len));
              pts.push(a.clone().lerp(p, 1 - 1.5 / len));
            }
          }
        }
        pts.push(p);
      }
      const curve = new THREE.CatmullRomCurve3(pts, false, 'centripetal', 0.5);
      const segs = 600, radial = 10;
      const tubeGeo = track(new THREE.TubeGeometry(curve, segs, 0.25, radial, false));
      const tubeMat = track(new THREE.MeshStandardMaterial({
        color: COLORS.clay, emissive: COLORS.clay, emissiveIntensity: 1.1, roughness: 0.35,
      }));
      const tube = new THREE.Mesh(tubeGeo, tubeMat);
      tube.renderOrder = 100;
      tubeGeo.setDrawRange(0, 0);
      root.add(tube);

      const headGroup = new THREE.Group();
      const head = new THREE.Mesh(track(new THREE.SphereGeometry(0.42, 20, 14)),
        track(new THREE.MeshBasicMaterial({ color: 0xfff1e6 })));
      const glowTex = track(glowTexture());
      const glow = new THREE.Sprite(track(new THREE.SpriteMaterial({
        map: glowTex, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true,
      })));
      glow.scale.setScalar(4.2);
      headGroup.add(head, glow);
      headGroup.visible = false;
      root.add(headGroup);

      // destination pulse
      const dest = route[route.length - 1];
      const destPos = world(dest, 0.05);
      const pulseRing = new THREE.Mesh(track(new THREE.RingGeometry(0.6, 1.0, 40)),
        track(new THREE.MeshBasicMaterial({ color: COLORS.room, transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false, blending: THREE.AdditiveBlending })));
      pulseRing.rotation.x = -Math.PI / 2;
      pulseRing.position.copy(destPos).y += 0.12;
      root.add(pulseRing);
      const destGlow = new THREE.Sprite(track(new THREE.SpriteMaterial({
        map: glowTex, color: COLORS.room, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0,
      })));
      destGlow.position.copy(world(dest, 0.5));
      destGlow.scale.setScalar(5);
      root.add(destGlow);

      routeObj = { curve, tubeGeo, segs, radial, headGroup, pulseRing, destGlow, dest };
    }

    // view azimuth: look across the offset between the lowest and highest floor so the
    // stack separates vertically on screen, then turn 25 deg for a 3/4 view
    const c0 = floorGroups.get(floors[0]).userData.center;
    const c1 = floorGroups.get(floors[floors.length - 1]).userData.center;
    const dx = c1.x - c0.x, dz = c1.z - c0.z;
    if (Math.hypot(dx, dz) > 3) {
      let az = Math.atan2(dx, dz) + Math.PI / 2;      // camera direction perpendicular to the offset
      if (Math.cos(az) < 0) az += Math.PI;            // keep the camera on the south (+z) side
      fit.az = az + (Math.sin(az) >= 0 ? 0.45 : -0.45);
    }
    built = { root, floorGroups, floors, bounds, fitPts, vertLines, vertMat, routeObj };
    computeFit();
    cam.az = fit.az - 1.1;
    cam.el = 1.2;
    cam.dist = fit.dist * 1.9;
    applyCam();
    controls.target.copy(center);
  }

  // Fill gaps between consecutive route ids with the shortest path (3D length).
  function expandRoute(ids, data, nodes, floorY) {
    const adj = new Map();
    const pos = (n) => new THREE.Vector3(n.x, floorY(n.floor), -n.y);
    data.edges.forEach((e) => {
      const a = nodes.get(e.from), b = nodes.get(e.to);
      if (!a || !b) return;
      const w = pos(a).distanceTo(pos(b)) + (e.kind === 'outdoor' ? 50 : 0);
      if (!adj.has(a.id)) adj.set(a.id, []);
      if (!adj.has(b.id)) adj.set(b.id, []);
      adj.get(a.id).push([b.id, w]);
      adj.get(b.id).push([a.id, w]);
    });
    const shortest = (s, t) => {
      const dist = new Map([[s, 0]]), prev = new Map(), done = new Set();
      while (true) {
        let u = null, best = Infinity;
        for (const [k, v] of dist) if (!done.has(k) && v < best) { best = v; u = k; }
        if (u === null) return null;
        if (u === t) break;
        done.add(u);
        for (const [v, w] of adj.get(u) || []) {
          const nd = best + w;
          if (nd < (dist.get(v) ?? Infinity)) { dist.set(v, nd); prev.set(v, u); }
        }
      }
      const path = [t];
      while (path[0] !== s) path.unshift(prev.get(path[0]));
      return path;
    };
    const valid = ids.filter((id) => nodes.has(id));
    const out = [];
    for (let i = 0; i < valid.length; i++) {
      if (i === 0) { out.push(valid[0]); continue; }
      const a = valid[i - 1], b = valid[i];
      const direct = (adj.get(a) || []).some(([v]) => v === b);
      if (direct) { out.push(b); continue; }
      const p = shortest(a, b);
      if (p) out.push(...p.slice(1)); else out.push(b);
    }
    return out.map((id) => nodes.get(id));
  }

  // ---------- animation ----------
  function setRouteProgress(t) {
    const r = built && built.routeObj;
    if (!r) return;
    const count = Math.floor(t * r.segs) * r.radial * 6;
    r.tubeGeo.setDrawRange(0, count);
    r.headGroup.visible = t > 0.001 && t < 0.999;
    r.headGroup.position.copy(r.curve.getPointAt(Math.min(1, Math.max(0, t))));
  }

  function stopPulse() {
    if (pulseTl) { pulseTl.kill(); pulseTl = null; }
    const r = built && built.routeObj;
    if (r) {
      r.pulseRing.material.opacity = 0;
      r.pulseRing.scale.setScalar(1);
      r.destGlow.material.opacity = 0;
    }
    labelDivs.forEach((d) => d.classList.remove('dest'));
  }

  function startPulse() {
    const r = built.routeObj;
    stopPulse();
    labelDivs.forEach((d, i) => {
      // highlight the destination label
      if (d.textContent === shortName(r.dest)) d.classList.add('dest');
    });
    pulseTl = gsap.timeline({ repeat: -1 });
    pulseTl.fromTo(r.pulseRing.scale, { x: 0.6, y: 0.6, z: 0.6 }, { x: 4.5, y: 4.5, z: 4.5, duration: 1.4, ease: 'power2.out' }, 0)
      .fromTo(r.pulseRing.material, { opacity: 0.95 }, { opacity: 0, duration: 1.4, ease: 'power1.in' }, 0)
      .fromTo(r.destGlow.material, { opacity: 1 }, { opacity: 0.45, duration: 0.7, ease: 'sine.inOut', yoyo: true, repeat: 1 }, 0);
  }

  function buildRouteTl() {
    if (routeTl) routeTl.kill();
    const prog = { t: 0 };
    setRouteProgress(0);
    stopPulse();
    routeTl = gsap.timeline({
      paused: true,
      onComplete: () => {
        phase = 'done';
        controls.autoRotate = true;
      },
    });
    if (built.routeObj) {
      routeTl.to(prog, { t: 1, duration: 3, ease: 'power1.inOut', onUpdate: () => setRouteProgress(prog.t) });
      routeTl.add(() => startPulse());
    }
    return routeTl;
  }

  function buildIntroTl() {
    const groups = built.floors.map((f) => built.floorGroups.get(f));
    groups.forEach((g) => { g.position.y = 0; });
    labelDivs.concat(floorLabelDivs).forEach((d) => { d.style.opacity = 0; });
    built.vertMat.opacity = 0;
    cam.az = fit.az - 1.1; cam.el = 1.2; cam.dist = fit.dist * 1.9;
    applyCam();
    controls.enabled = false;
    controls.autoRotate = false;
    introTl = gsap.timeline({
      paused: true,
      onComplete: () => {
        controls.target.copy(center);
        controls.enabled = true;
        controls.update();
        phase = 'route';
        buildRouteTl().play();
      },
    });
    introTl.to(groups.map((g) => g.position), {
      y: (i) => groups[i].userData.targetY, duration: 1.2, ease: 'power3.out', stagger: 0.18,
    }, 0.15);
    introTl.to(cam, {
      az: fit.az, el: fit.el, dist: fit.dist, duration: 2.4, ease: 'power2.inOut', onUpdate: applyCam,
    }, 0);
    introTl.to(built.vertMat, { opacity: 0.55, duration: 0.6 }, 1.2);
    introTl.to(floorLabelDivs, { opacity: 1, duration: 0.5 }, 1.2);
    introTl.to(labelDivs, { opacity: 1, duration: 0.6, stagger: 0.02 }, 1.4);
    return introTl;
  }

  // ---------- public API ----------
  function play() {
    if (destroyed) return;
    wantPlay = true;
    if (!built) return;           // plays once data arrives
    startLoop();
    if (phase === 'idle') {
      if (!el.clientWidth || !el.clientHeight) return; // resize() starts it once visible
      computeFit();
      phase = 'intro';
      buildIntroTl().play();
    } else if (phase === 'intro') {
      introTl && introTl.resume();
    } else if (phase === 'route') {
      routeTl && routeTl.resume();
      pulseTl && pulseTl.resume();
    } else if (phase === 'done') {
      // replay the route from the start, camera stays where the presenter left it
      controls.autoRotate = false;
      phase = 'route';
      buildRouteTl().play();
    }
  }

  function pause() {
    wantPlay = false;
    stopLoop();
    introTl && introTl.pause();
    routeTl && routeTl.pause();
    pulseTl && pulseTl.pause();
  }

  function destroy() {
    if (destroyed) return;
    pause();
    destroyed = true;
    introTl && introTl.kill();
    routeTl && routeTl.kill();
    pulseTl && pulseTl.kill();
    ro.disconnect();
    controls.dispose();
    disposables.forEach((d) => d.dispose && d.dispose());
    renderer.dispose();
    renderer.forceContextLoss && renderer.forceContextLoss();
    scene.clear();
    renderer.domElement.remove();
    labelRenderer.domElement.remove();
  }

  resize();
  fetch('./data/' + code + '.json')
    .then((r) => { if (!r.ok) throw new Error('building3d: HTTP ' + r.status + ' for ' + code); return r.json(); })
    .then((data) => {
      if (destroyed) return;
      build(data);
      resize();
      if (wantPlay) play(); else renderOnce();
    })
    .catch((err) => console.error(err));

  return { play, pause, destroy };
}

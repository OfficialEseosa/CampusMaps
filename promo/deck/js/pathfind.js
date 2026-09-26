// pathfind.js, canvas-2D Dijkstra frontier player on a building-like grid.
// Contract: mount(el, opts) -> { play(), pause(), destroy() }
// Act 1: search from outside, trace route via Main entrance + elevator to 608.
// Act 2: clock scrubs to Sat 21:00, Main goes card-only, search re-runs, route swings to West + stairs.

const C = {
  ink: '#1b1614', clay: '#c67c4e', warm: '#ffb68c', teal: '#7ad0e0',
  gold: '#ffd166', err: '#e05a4e',
};

// ---------------- map (deterministic) ----------------
const W = 33, H = 12;
const F1 = 3;   // floor 1 left wall column (cols 3..16)
const F6 = 19;  // floor 6 left wall column (cols 19..32)
// cell kinds
const VOID = 0, OUT = 1, HALL = 2, ROOM = 3, WALL = 4, DOOR = 5, ELEV = 6, STAIR = 7, DEST = 8;

function buildMap() {
  const kind = new Uint8Array(W * H);
  const id = (c, r) => r * W + c;
  const set = (c, r, k) => { kind[id(c, r)] = k; };
  for (let r = 0; r < H; r++) for (let c = 0; c < 3; c++) set(c, r, OUT);
  for (const F of [F1, F6]) {
    for (let r = 0; r < H; r++) for (let lc = 0; lc < 14; lc++) {
      const edge = lc === 0 || lc === 13 || r === 0 || r === 11;
      set(F + lc, r, edge ? WALL : ROOM);
    }
  }
  const hall = (F, lc0, lc1, r0, r1) => {
    for (let r = r0; r <= r1; r++) for (let lc = lc0; lc <= lc1; lc++) set(F + lc, r, HALL);
  };
  // Floor 1: two long halls, two cross halls, lobby row + atrium
  hall(F1, 1, 12, 2, 2); hall(F1, 1, 12, 9, 9);
  hall(F1, 3, 3, 2, 9); hall(F1, 10, 10, 2, 9);
  hall(F1, 3, 12, 5, 5); hall(F1, 5, 8, 4, 7);
  set(F1 + 12, 1, STAIR); set(F1 + 12, 5, ELEV);
  set(F1, 2, DOOR); set(F1, 9, DOOR);          // West (row 2), Main (row 9)
  // Floor 6
  hall(F6, 1, 12, 2, 2); hall(F6, 1, 12, 9, 9);
  hall(F6, 4, 4, 2, 9); hall(F6, 9, 9, 2, 9);
  hall(F6, 1, 9, 5, 5);
  set(F6 + 1, 1, STAIR); set(F6 + 1, 5, ELEV);
  set(F6 + 10, 7, DOOR); set(F6 + 11, 7, DEST);
  return kind;
}

const KIND = buildMap();
const idx = (c, r) => r * W + c;
const START = idx(1, 10);
const TARGET = idx(F6 + 11, 7);
const MAIN = idx(F1, 9);
const WEST = idx(F1, 2);
const JUMPS = [
  [idx(F1 + 12, 5), idx(F6 + 1, 5), 13, 'elevator'],
  [idx(F1 + 12, 1), idx(F6 + 1, 1), 10, 'stairs'],
];
const walkable = (k) => k === OUT || k === HALL || k === DOOR || k === ELEV || k === STAIR || k === DEST;

// Rooms: connected components of ROOM cells (for outlines and labels)
const ROOM_ID = new Int16Array(W * H).fill(-1);
const ROOMS = [];
(function () {
  for (let i = 0; i < W * H; i++) {
    if (KIND[i] !== ROOM || ROOM_ID[i] >= 0) continue;
    const rid = ROOMS.length, cells = [i], q = [i];
    ROOM_ID[i] = rid;
    while (q.length) {
      const j = q.pop(), c = j % W, r = (j / W) | 0;
      for (const [dc, dr] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nc = c + dc, nr = r + dr;
        if (nc < 0 || nr < 0 || nc >= W || nr >= H) continue;
        const n = idx(nc, nr);
        if (KIND[n] === ROOM && ROOM_ID[n] < 0) { ROOM_ID[n] = rid; cells.push(n); q.push(n); }
      }
    }
    ROOMS.push(cells);
  }
})();
// 608 room block: the ROOM cells around DEST are rendered as the destination room
const IS_608 = (c, r) => c >= F6 + 10 && c <= F6 + 12 && r >= 6 && r <= 8;

// ---------------- Dijkstra (offline, recorded) ----------------
function neighbours(i, blocked) {
  const c = i % W, r = (i / W) | 0, out = [];
  for (const [dc, dr] of [[1, 0], [0, -1], [-1, 0], [0, 1]]) {
    const nc = c + dc, nr = r + dr;
    if (nc < 0 || nr < 0 || nc >= W || nr >= H) continue;
    const n = idx(nc, nr);
    if (walkable(KIND[n]) && !blocked.has(n)) out.push([n, 1]);
  }
  for (const [a, b, w] of JUMPS) {
    if (i === a) out.push([b, w]);
    if (i === b) out.push([a, w]);
  }
  return out;
}

function dijkstra(blocked) {
  const N = W * H;
  const dist = new Float64Array(N).fill(Infinity);
  const prev = new Int32Array(N).fill(-1);
  const closed = new Uint8Array(N);
  const openStep = new Int32Array(N).fill(-1);
  const closeStep = new Int32Array(N).fill(-1);
  const open = new Set([START]);
  const iters = []; // {current, opened:[...]}, closed/open sets are derived from openStep/closeStep
  dist[START] = 0; openStep[START] = 0;
  while (open.size) {
    let cur = -1, best = Infinity;
    for (const o of open) if (dist[o] < best || (dist[o] === best && o < cur)) { best = dist[o]; cur = o; }
    open.delete(cur);
    closed[cur] = 1;
    const step = iters.length;
    closeStep[cur] = step;
    const opened = [];
    if (cur === TARGET) { iters.push({ current: cur, opened }); break; }
    for (const [n, w] of neighbours(cur, blocked)) {
      if (closed[n]) continue;
      const nd = dist[cur] + w;
      if (nd < dist[n]) {
        dist[n] = nd; prev[n] = cur;
        if (!open.has(n)) { open.add(n); opened.push(n); openStep[n] = step; }
      }
    }
    iters.push({ current: cur, opened });
  }
  const path = [];
  for (let p = TARGET; p >= 0; p = prev[p]) path.push(p);
  path.reverse();
  return { iters, openStep, closeStep, path, cost: dist[TARGET] };
}

const RUN1 = dijkstra(new Set());
const RUN2 = dijkstra(new Set([MAIN]));
const fmt = (s) => `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, '0')}`;
const T1 = '1:27';
const T2 = fmt((87 * RUN2.cost) / RUN1.cost);

// ---------------- timeline (ms) ----------------
const STEPS_PER_SEC = 240; // ~4 steps per frame at 60 fps
const D = {};
{
  let t = 0;
  const add = (k, d) => { D[k] = [t, t + d]; t += d; };
  add('intro', 600);
  add('search1', (RUN1.iters.length / STEPS_PER_SEC) * 1000);
  add('trace1', 1300);
  add('hold1', 1000);
  add('clock', 1500);
  add('lock', 700);
  add('gap', 400);
  add('search2', (RUN2.iters.length / STEPS_PER_SEC) * 1000);
  add('trace2', 1300);
  add('banner', 600);
  D.end = t;
}
const prog = (k, t) => Math.max(0, Math.min(1, (t - D[k][0]) / (D[k][1] - D[k][0])));
const easeIO = (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
const easeOut = (x) => 1 - Math.pow(1 - x, 3);
const backOut = (x) => { const s = 1.9; return 1 + (s + 1) * Math.pow(x - 1, 3) + s * Math.pow(x - 1, 2); };

// ---------------- mount ----------------
export function mount(el, opts = {}) {
  const canvas = document.createElement('canvas');
  canvas.style.display = 'block';
  canvas.style.width = '100%';
  canvas.style.height = '100%';
  el.appendChild(canvas);
  const g = canvas.getContext('2d');

  let cssW = 0, cssH = 0, dpr = 1, L = null;
  let t = 0, raf = 0, last = 0, running = false;

  function layout() {
    // clientWidth/Height = layout size, unaffected by reveal.js's CSS scale transform
    cssW = Math.max(200, el.clientWidth || 0); cssH = Math.max(120, el.clientHeight || 0);
    dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width = Math.round(cssW * dpr);
    canvas.height = Math.round(cssH * dpr);
    const top = 50, bottom = 74;
    const cs = Math.max(6, Math.floor(Math.min(cssW / (W + 0.4), (cssH - top - bottom) / H)));
    const ox = Math.round((cssW - cs * W) / 2);
    const oy = Math.round(top + (cssH - top - bottom - cs * H) / 2);
    L = { cs, ox, oy, top, bottom };
  }
  const cx = (i) => L.ox + ((i % W) + 0.5) * L.cs;
  const cy = (i) => L.oy + (((i / W) | 0) + 0.5) * L.cs;
  const font = (w, px) => `${w} ${px}px Sora, system-ui, sans-serif`;

  function rrect(x, y, w, h, r) {
    g.beginPath();
    g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
    g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
  }

  // ----- static map -----
  function drawMap(alpha, locked) {
    const { cs, ox, oy } = L;
    g.save();
    g.globalAlpha = alpha;
    // outside strip
    g.fillStyle = 'rgba(122,208,224,0.05)';
    rrect(ox + 2, oy + 2, cs * 3 - 8, cs * H - 4, 10); g.fill();
    g.fillStyle = 'rgba(255,255,255,0.38)';
    g.font = font(500, Math.max(10, cs * 0.26));
    g.textAlign = 'center';
    g.fillText('outside', ox + cs * 1.45, oy + cs * 0.55);
    // floor panels
    for (const [F, name] of [[F1, 'Floor 1'], [F6, 'Floor 6']]) {
      const x = ox + F * cs, y = oy, w = cs * 14, h = cs * H;
      g.fillStyle = 'rgba(255,255,255,0.028)';
      rrect(x, y, w, h, 8); g.fill();
      g.strokeStyle = 'rgba(255,255,255,0.3)'; g.lineWidth = 2;
      rrect(x + 1, y + 1, w - 2, h - 2, 8); g.stroke();
      g.fillStyle = 'rgba(255,255,255,0.78)';
      g.font = font(700, Math.max(12, cs * 0.36));
      g.textAlign = 'left';
      g.fillText(name, x + 4, y - cs * 0.28);
    }
    // cells
    for (let i = 0; i < W * H; i++) {
      const k = KIND[i], c = i % W, r = (i / W) | 0;
      const x = ox + c * cs, y = oy + r * cs;
      if (k === HALL || k === ELEV || k === STAIR || k === DOOR || k === DEST) {
        g.fillStyle = 'rgba(255,255,255,0.075)';
        g.fillRect(x, y, cs, cs);
      }
      if (k === ROOM) {
        g.fillStyle = IS_608(c, r) ? 'rgba(255,209,102,0.07)' : 'rgba(255,255,255,0.018)';
        g.fillRect(x, y, cs, cs);
      }
    }
    // room outlines (edges between room and non-room)
    g.strokeStyle = 'rgba(255,255,255,0.16)'; g.lineWidth = 1;
    g.beginPath();
    for (let i = 0; i < W * H; i++) {
      if (KIND[i] !== ROOM && KIND[i] !== DEST) continue;
      const c = i % W, r = (i / W) | 0, x = ox + c * cs, y = oy + r * cs;
      const same = (nc, nr) => {
        if (nc < 0 || nr < 0 || nc >= W || nr >= H) return false;
        const n = idx(nc, nr);
        if (IS_608(c, r)) return IS_608(nc, nr);
        return KIND[n] === ROOM && ROOM_ID[n] === ROOM_ID[i] && !IS_608(nc, nr);
      };
      if (!same(c, r - 1)) { g.moveTo(x + 0.5, y + 0.5); g.lineTo(x + cs + 0.5, y + 0.5); }
      if (!same(c - 1, r)) { g.moveTo(x + 0.5, y + 0.5); g.lineTo(x + 0.5, y + cs + 0.5); }
    }
    g.stroke();
    // subdivide the big room blocks into offices (every 3 cells along halls)
    g.strokeStyle = 'rgba(255,255,255,0.07)';
    g.beginPath();
    for (let i = 0; i < W * H; i++) {
      if (KIND[i] !== ROOM || IS_608(i % W, (i / W) | 0)) continue;
      const c = i % W, r = (i / W) | 0;
      if (c % 3 === 0 && KIND[i - 1] === ROOM && ROOM_ID[i - 1] === ROOM_ID[i]) {
        const x = ox + c * cs + 0.5; g.moveTo(x, oy + r * cs); g.lineTo(x, oy + (r + 1) * cs);
      }
    }
    g.stroke();
    // 608
    const d = TARGET;
    g.strokeStyle = C.gold; g.lineWidth = 2;
    rrect(ox + (F6 + 10) * cs + 3, oy + 6 * cs + 3, cs * 3 - 6, cs * 3 - 6, 6); g.stroke();
    g.fillStyle = C.gold;
    g.font = font(700, Math.max(12, cs * 0.34));
    g.textAlign = 'center';
    g.fillText('608', ox + (F6 + 11.5) * cs, oy + 6 * cs + cs * 0.62);
    // jump connectors
    g.setLineDash([4, 5]); g.lineWidth = 1.5;
    for (const [a, b, , name] of JUMPS) {
      g.strokeStyle = 'rgba(255,255,255,0.35)';
      g.beginPath(); g.moveTo(cx(a) + cs * 0.35, cy(a)); g.lineTo(cx(b) - cs * 0.35, cy(b)); g.stroke();
    }
    g.setLineDash([]);
    for (const [a, b, , name] of JUMPS) {
      g.fillStyle = 'rgba(255,255,255,0.62)';
      g.font = font(500, Math.max(10, cs * 0.25));
      g.textAlign = 'center';
      g.fillText(name, (cx(a) + cx(b)) / 2, cy(a) - cs * 0.22);
      for (const n of [a, b]) drawIcon(n, name);
    }
    // entrances
    for (const [n, name] of [[WEST, 'West'], [MAIN, 'Main']]) {
      const isLocked = n === MAIN && locked > 0;
      g.fillStyle = isLocked ? `rgba(224,90,78,${0.25 + 0.35 * locked})` : 'rgba(122,208,224,0.35)';
      g.fillRect(ox + (n % W) * cs + 2, oy + ((n / W) | 0) * cs + 2, cs - 4, cs - 4);
      g.fillStyle = isLocked ? C.err : C.teal;
      g.font = font(700, Math.max(11, cs * 0.3));
      g.textAlign = 'right';
      g.fillText(name, cx(n) - cs * 0.62, cy(n) - cs * 0.32);
    }
    // start
    g.fillStyle = C.teal;
    g.beginPath(); g.arc(cx(START), cy(START), cs * 0.22, 0, Math.PI * 2); g.fill();
    g.strokeStyle = 'rgba(122,208,224,0.45)'; g.lineWidth = 2;
    g.beginPath(); g.arc(cx(START), cy(START), cs * 0.36, 0, Math.PI * 2); g.stroke();
    g.fillStyle = 'rgba(255,255,255,0.85)';
    g.font = font(700, Math.max(11, cs * 0.28));
    g.textAlign = 'center';
    g.fillText('You', cx(START), cy(START) + cs * 0.78);
    g.restore();
  }

  function drawIcon(n, name) {
    const { cs } = L, x = cx(n), y = cy(n), s = cs * 0.26;
    g.save();
    g.strokeStyle = 'rgba(255,255,255,0.8)'; g.fillStyle = 'rgba(255,255,255,0.8)'; g.lineWidth = 1.6;
    if (name === 'elevator') {
      rrect(x - s, y - s, 2 * s, 2 * s, 3); g.stroke();
      g.beginPath(); g.moveTo(x - s * 0.45, y - s * 0.1); g.lineTo(x, y - s * 0.65); g.lineTo(x + s * 0.45, y - s * 0.1); g.fill();
      g.beginPath(); g.moveTo(x - s * 0.45, y + s * 0.15); g.lineTo(x, y + s * 0.7); g.lineTo(x + s * 0.45, y + s * 0.15); g.fill();
    } else {
      g.beginPath();
      g.moveTo(x - s, y + s); g.lineTo(x - s * 0.33, y + s); g.lineTo(x - s * 0.33, y + s * 0.33);
      g.lineTo(x + s * 0.33, y + s * 0.33); g.lineTo(x + s * 0.33, y - s * 0.33); g.lineTo(x + s, y - s * 0.33);
      g.lineTo(x + s, y - s);
      g.stroke();
    }
    g.restore();
  }

  function drawLock(n, a) {
    const { cs } = L, x = cx(n), y = cy(n), s = cs * 0.2 * backOut(a);
    if (a <= 0) return;
    g.save();
    g.globalAlpha = Math.min(1, a * 1.5);
    g.fillStyle = C.err; g.strokeStyle = '#fff'; g.lineWidth = Math.max(1.5, cs * 0.05);
    g.beginPath(); g.arc(x, y - s * 0.25, s * 0.62, Math.PI, 0); g.stroke();
    rrect(x - s, y - s * 0.25, 2 * s, s * 1.5, s * 0.25); g.fillStyle = '#fff'; g.fill();
    g.fillStyle = C.err; g.beginPath(); g.arc(x, y + s * 0.4, s * 0.22, 0, Math.PI * 2); g.fill();
    // label
    const label = 'card-only now';
    g.font = font(700, Math.max(11, cs * 0.28));
    const tw = g.measureText(label).width, px = cs * 0.2;
    const bx = L.ox + (F1 + 0.15) * cs, by = cy(n) + cs * 0.62;
    rrect(bx, by, tw + px * 2, cs * 0.5, cs * 0.25);
    g.fillStyle = 'rgba(224,90,78,0.9)'; g.fill();
    g.fillStyle = '#fff'; g.textAlign = 'left'; g.textBaseline = 'middle';
    g.fillText(label, bx + px, by + cs * 0.26);
    g.restore();
  }

  // ----- search frontier -----
  function drawSearch(run, stepF, alpha) {
    const { cs, ox, oy } = L;
    const step = Math.floor(stepF);
    const cur = run.iters[Math.min(step, run.iters.length - 1)].current;
    g.save();
    g.globalAlpha = alpha;
    for (let i = 0; i < W * H; i++) {
      const cS = run.closeStep[i], oS = run.openStep[i];
      if (oS < 0 || oS > step) continue;
      const x = ox + (i % W) * cs, y = oy + ((i / W) | 0) * cs;
      if (cS >= 0 && cS <= step) {
        g.fillStyle = 'rgba(198,124,78,0.35)';
        g.fillRect(x + 1, y + 1, cs - 2, cs - 2);
      } else {
        g.fillStyle = 'rgba(255,182,140,0.2)';
        g.fillRect(x + 1, y + 1, cs - 2, cs - 2);
        g.strokeStyle = 'rgba(255,182,140,0.75)'; g.lineWidth = 1.5;
        g.strokeRect(x + 2, y + 2, cs - 4, cs - 4);
      }
    }
    if (step < run.iters.length - 1 || alpha >= 1) {
      const x = ox + (cur % W) * cs, y = oy + ((cur / W) | 0) * cs;
      g.shadowColor = C.warm; g.shadowBlur = 16;
      g.fillStyle = '#ffe2cf';
      g.fillRect(x + 2, y + 2, cs - 4, cs - 4);
    }
    g.restore();
  }

  // ----- route stroke -----
  function routePts(path) { return path.map((i) => [cx(i), cy(i)]); }
  function strokePartial(pts, f, style, width, dash) {
    let total = 0; const seg = [];
    for (let i = 1; i < pts.length; i++) {
      const d = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
      seg.push(d); total += d;
    }
    let left = total * f;
    g.save();
    g.strokeStyle = style; g.lineWidth = width; g.lineCap = 'round'; g.lineJoin = 'round';
    if (dash) g.setLineDash(dash);
    g.beginPath(); g.moveTo(pts[0][0], pts[0][1]);
    let end = pts[0];
    for (let i = 1; i < pts.length && left > 0; i++) {
      const k = Math.min(1, left / seg[i - 1]);
      end = [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * k, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * k];
      g.lineTo(end[0], end[1]);
      left -= seg[i - 1];
    }
    g.stroke();
    g.restore();
    return end;
  }
  function drawRoute(run, f, alpha) {
    const pts = routePts(run.path), cs = L.cs;
    g.save(); g.globalAlpha = alpha;
    g.shadowColor = 'rgba(198,124,78,0.8)'; g.shadowBlur = 14;
    const end = strokePartial(pts, easeIO(f), C.clay, cs * 0.28);
    g.shadowBlur = 0;
    strokePartial(pts, easeIO(f), 'rgba(255,214,190,0.8)', cs * 0.07);
    // head
    g.fillStyle = '#fff';
    g.beginPath(); g.arc(end[0], end[1], cs * 0.12, 0, Math.PI * 2); g.fill();
    g.restore();
  }
  function drawTimeLabel(text, a) {
    if (a <= 0) return;
    a = Math.min(1, a);
    const cs = L.cs, x = cx(TARGET), y = oyRow(6) - cs * 0.15;
    g.save();
    g.globalAlpha = a;
    g.font = font(700, Math.max(13, cs * 0.4));
    const tw = g.measureText(text).width, pw = tw + cs * 0.5, ph = cs * 0.62;
    const bx = x - pw / 2, by = y - ph - (1 - a) * 8;
    rrect(bx, by, pw, ph, ph / 2); g.fillStyle = C.clay; g.fill();
    g.fillStyle = '#fff'; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillText(text, x, by + ph / 2 + 1);
    g.restore();
  }
  const oyRow = (r) => L.oy + r * L.cs;

  function drawClock(t) {
    const p = easeIO(prog('clock', t));
    const mins = 14 * 60 + Math.round(p * 7 * 60);
    const hh = Math.floor(mins / 60), mm = mins % 60;
    const text = `Sat ${String(hh).padStart(2, '0')}:${String(Math.floor(mm / 5) * 5).padStart(2, '0')}`;
    const cs = L.cs;
    const rightX = L.ox + W * cs, y = 8;
    const active = t >= D.clock[0] && t < D.lock[1];
    g.save();
    g.font = font(700, 20);
    const tw = g.measureText('Sat 00:00').width;
    const pw = tw + 58, ph = 36, bx = rightX - pw;
    rrect(bx, y, pw, ph, 18);
    g.fillStyle = active ? 'rgba(255,209,102,0.16)' : 'rgba(255,255,255,0.06)'; g.fill();
    g.strokeStyle = active ? C.gold : 'rgba(255,255,255,0.2)'; g.lineWidth = 1.5; g.stroke();
    // clock glyph
    const kx = bx + 22, ky = y + ph / 2;
    g.strokeStyle = active ? C.gold : 'rgba(255,255,255,0.75)'; g.lineWidth = 2;
    g.beginPath(); g.arc(kx, ky, 9, 0, Math.PI * 2); g.stroke();
    const ha = ((mins / 60) % 12) / 12 * Math.PI * 2 - Math.PI / 2, ma = (mm / 60) * Math.PI * 2 - Math.PI / 2;
    g.lineCap = 'round';
    g.beginPath(); g.moveTo(kx, ky); g.lineTo(kx + Math.cos(ha) * 4.5, ky + Math.sin(ha) * 4.5);
    g.moveTo(kx, ky); g.lineTo(kx + Math.cos(ma) * 7, ky + Math.sin(ma) * 7); g.stroke();
    g.fillStyle = '#fff'; g.textAlign = 'left'; g.textBaseline = 'middle';
    g.fillText(text, bx + 40, ky + 1);
    g.restore();
  }

  function drawBanner(a) {
    if (a <= 0) return;
    const text = 'Heads up: Main entrance is card-only now. Using West entrance instead.';
    g.save();
    g.globalAlpha = a;
    let fs = 20;
    g.font = font(700, fs);
    let tw = g.measureText(text).width;
    const maxW = cssW - 120;
    if (tw > maxW) { fs = Math.max(11, fs * maxW / tw); g.font = font(700, fs); tw = g.measureText(text).width; }
    const pw = tw + 80, ph = fs + 26;
    const bx = (cssW - pw) / 2, by = cssH - ph - 10 + (1 - a) * 12;
    rrect(bx, by, pw, ph, ph / 2);
    g.fillStyle = 'rgba(40,30,26,0.96)'; g.fill();
    g.strokeStyle = C.gold; g.lineWidth = 1.5; g.stroke();
    // warning triangle
    const tx = bx + 30, ty = by + ph / 2;
    g.fillStyle = C.gold;
    g.beginPath(); g.moveTo(tx, ty - 9); g.lineTo(tx + 10, ty + 8); g.lineTo(tx - 10, ty + 8); g.closePath(); g.fill();
    g.fillStyle = C.ink; g.font = font(700, 12); g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillText('!', tx, ty + 2);
    g.fillStyle = '#fff'; g.font = font(700, fs); g.textAlign = 'left';
    g.fillText(text, bx + 52, ty + 1);
    g.restore();
  }

  // ----- frame -----
  function render() {
    if (!L) return;
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, cssW, cssH);
    const intro = easeOut(prog('intro', t));
    const lockA = prog('lock', t);
    drawMap(intro, lockA);

    const act2 = t >= D.search2[0];
    if (!act2) {
      if (t >= D.search1[0]) {
        const stepF = ((t - D.search1[0]) / 1000) * STEPS_PER_SEC;
        const fade = 1 - 0.55 * prog('trace1', t) - 0.25 * prog('gap', t);
        drawSearch(RUN1, Math.min(stepF, RUN1.iters.length - 1), fade);
      }
      if (t >= D.trace1[0]) {
        drawRoute(RUN1, prog('trace1', t), 1);
        drawTimeLabel(T1, easeOut(Math.max(0, (t - D.trace1[1] + 250) / 350)) * (1 - prog('gap', t)));
      }
    } else {
      // dimmed alternative
      g.save(); g.globalAlpha = 0.9;
      strokePartial(routePts(RUN1.path), 1, 'rgba(255,255,255,0.2)', L.cs * 0.14, [L.cs * 0.18, L.cs * 0.22]);
      g.restore();
      const stepF = ((t - D.search2[0]) / 1000) * STEPS_PER_SEC;
      drawSearch(RUN2, Math.min(stepF, RUN2.iters.length - 1), 1 - 0.55 * prog('trace2', t));
      if (t >= D.trace2[0]) {
        drawRoute(RUN2, prog('trace2', t), 1);
        drawTimeLabel(T2, easeOut(Math.max(0, (t - D.trace2[1] + 250) / 350)));
      }
    }
    // entrance lock overlay on top of everything else
    drawLock(MAIN, lockA);
    drawClock(t);
    drawBanner(easeOut(prog('banner', t)));
  }

  function loop(now) {
    if (!running) return;
    const dt = Math.min(64, now - last);
    last = now;
    t += dt;
    render();
    if (t >= D.end) { running = false; raf = 0; return; }
    raf = requestAnimationFrame(loop);
  }

  function play() {
    t = 0;
    running = true;
    last = performance.now();
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(loop);
  }
  function pause() {
    running = false;
    cancelAnimationFrame(raf);
    raf = 0;
  }
  function destroy() {
    pause();
    ro.disconnect();
    canvas.remove();
  }

  // seek(ms): jump to a frame and hold it (paused). Handy for posters and tests.
  function seek(ms) { pause(); t = Math.max(0, Math.min(D.end, ms)); render(); }

  const ro = new ResizeObserver(() => { layout(); render(); });
  ro.observe(el);
  layout();
  // Before play(), show the final state (static poster); play() restarts from the beginning.
  t = opts.poster === false ? 0 : D.end;
  render();
  if (document.fonts && document.fonts.load) {
    Promise.all([document.fonts.load(font(700, 16)), document.fonts.load(font(500, 16))])
      .then(() => { if (!running) render(); }).catch(() => {});
  }
  if (opts.autoplay) play();
  return { play, pause, destroy, seek, duration: D.end };
}

export const _debug = { RUN1, RUN2, D, T2 };

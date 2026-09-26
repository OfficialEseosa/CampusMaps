// anchors.js: "Camera burst -> anchor match, lock-on".
// A 6-frame burst flies in, the winning frame (KL-A03, sign "1116W") grows to
// the centre, feature points light up, match lines run to the reference
// thumbnail, and a viewfinder reticle snaps onto the sign and turns gold.
// Contract: mount(el, opts) -> { play(), pause(), destroy() }
// Everything is laid out on a 1400x650 design stage scaled to fit el.

const STYLE_ID = 'anchors-style';
const W = 1400, H = 650;
const CSS = `
.anc{position:relative;width:100%;height:100%;overflow:hidden;background:transparent;font-family:'Sora',system-ui,sans-serif;color:#fff}
.anc *{box-sizing:border-box}
.anc-stage{position:absolute;left:0;top:0;width:${W}px;height:${H}px;transform-origin:0 0}
.anc-tile{position:absolute;border-radius:14px;background-repeat:no-repeat;background-color:#2a221f;
  box-shadow:0 10px 30px rgba(0,0,0,.45);border:1px solid rgba(255,255,255,.12);will-change:transform}
.anc-tile .anc-idx{position:absolute;left:8px;top:6px;font-size:11px;font-weight:700;color:#fff;
  text-shadow:0 1px 4px rgba(0,0,0,.8);opacity:.85}
.anc-win-ring{position:absolute;inset:-3px;border-radius:16px;border:3px solid #ffd166;opacity:0;pointer-events:none}
.anc-head{position:absolute;font-size:14px;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:rgba(255,255,255,.55)}
.anc-head b{color:#ffb68c;font-weight:700}
.anc-canvas{position:absolute;z-index:10;left:0;top:0;width:${W}px;height:${H}px;pointer-events:none}
.anc-ghost{position:absolute;border-radius:14px;border:1.5px dashed rgba(255,209,102,.45)}
.anc-thumb{position:absolute;border-radius:12px;background-repeat:no-repeat;border:2px solid rgba(122,208,224,.7);
  box-shadow:0 10px 30px rgba(0,0,0,.45)}
.anc-thumb-label{position:absolute;font-size:15px;font-weight:500;color:rgba(255,255,255,.85);text-align:center;line-height:1.35}
.anc-thumb-label b{color:#7ad0e0;font-weight:700}
.anc-ret{position:absolute;z-index:11;pointer-events:none;color:#fff;filter:drop-shadow(0 0 6px rgba(0,0,0,.6))}
.anc-ret i{position:absolute;width:24px;height:24px;border:0 solid currentColor}
.anc-ret i:nth-child(1){left:0;top:0;border-left-width:4px;border-top-width:4px;border-top-left-radius:6px}
.anc-ret i:nth-child(2){right:0;top:0;border-right-width:4px;border-top-width:4px;border-top-right-radius:6px}
.anc-ret i:nth-child(3){left:0;bottom:0;border-left-width:4px;border-bottom-width:4px;border-bottom-left-radius:6px}
.anc-ret i:nth-child(4){right:0;bottom:0;border-right-width:4px;border-bottom-width:4px;border-bottom-right-radius:6px}
.anc-lock{position:absolute;display:flex;align-items:center;justify-content:center;gap:10px;font-size:22px;font-weight:700;color:#ffd166;white-space:nowrap}
.anc-lock svg{width:22px;height:22px}
.anc-chips{position:absolute;display:flex;justify-content:center;gap:12px}
.anc-chip{padding:8px 16px;border-radius:999px;font-size:15px;font-weight:500;white-space:nowrap;
  border:1.5px solid rgba(255,255,255,.22);color:rgba(255,255,255,.55);background:rgba(255,255,255,.03)}
`;

function injectStyle() {
  if (document.getElementById(STYLE_ID)) return;
  const s = document.createElement('style');
  s.id = STYLE_ID;
  s.textContent = CSS;
  document.head.appendChild(s);
}

// Deterministic LCG in [0,1)
function lcg(seed) {
  let s = seed >>> 0;
  return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}

// Source image sizes (px) for cover maths.
const IMG = {
  'KL-A01': [1600, 1280],
  'KL-A02': [1200, 1600],
  'KL-A03': [1200, 1600],
};

function cover(key, bw, bh) {
  const [iw, ih] = IMG[key];
  const k = Math.max(bw / iw, bh / ih);
  const w = iw * k, h = ih * k;
  return { size: `${w.toFixed(1)}px ${h.toFixed(1)}px`, pos: `${((bw - w) / 2).toFixed(1)}px ${((bh - h) / 2).toFixed(1)}px` };
}

// Layout (design px)
const TILE_W = 180, TILE_H = 120, GRID_X = 30, GRID_Y = 110, GAP = 16;
const BIG = { x: 470, y: 120, w: 440, h: 300 };
const BIG_K = 0.5833; // image scale inside the big tile (sign framed, not plain cover)
const BIG_OY = 352; // vertical offset (scaled px) of the big view into the image
const THUMB = { x: 1110, y: 120, w: 210, h: 270, k: 0.5, sx: 0, sy: 560 }; // crop of KL-A03 around the sign
const SIGN = { x0: 75, y0: 740, x1: 205, y1: 985 }; // "1116W" sign in source px

const toBig = (x, y) => [BIG.x + x * BIG_K, BIG.y + y * BIG_K - BIG_OY];
const toThumb = (x, y) => [THUMB.x + (x - THUMB.sx) * THUMB.k, THUMB.y + (y - THUMB.sy) * THUMB.k];

function makeFeatures(seed) {
  const r = lcg(seed);
  const vis = { x0: 8, x1: BIG.w / BIG_K - 8, y0: BIG_OY / BIG_K + 8, y1: (BIG_OY + BIG.h) / BIG_K - 8 };
  const pts = [];
  // 40 on the room sign (text-rich: most matches come from here)
  for (let i = 0; i < 40; i++) {
    pts.push({ x: SIGN.x0 + 6 + r() * (SIGN.x1 - SIGN.x0 - 12), y: SIGN.y0 + 6 + r() * (SIGN.y1 - SIGN.y0 - 12), sign: true });
  }
  // 14 around the door frame / handles / door poster
  for (let i = 0; i < 14; i++) {
    const pick = r();
    if (pick < 0.4) pts.push({ x: 410 + r() * 30, y: vis.y0 + r() * (vis.y1 - vis.y0) });
    else if (pick < 0.7) pts.push({ x: 585 + r() * 100, y: 685 + r() * 75 });
    else pts.push({ x: 690 + r() * 60, y: 870 + r() * 90 });
  }
  // the rest scattered (corners, edges of the recess)
  while (pts.length < 90) {
    const x = vis.x0 + r() * (vis.x1 - vis.x0), y = vis.y0 + r() * (vis.y1 - vis.y0);
    pts.push({ x, y });
  }
  const out = pts.map((p) => {
    const [bx, by] = toBig(p.x, p.y);
    return { ...p, bx, by, a: 0, g: 0 };
  });
  const matches = out.filter((p) => p.sign).slice(0, 25).map((p) => {
    const [tx, ty] = toThumb(p.x, p.y);
    return { p, tx, ty, t: 0 };
  });
  matches.forEach((m) => { m.p.matched = true; });
  return { pts: out, matches };
}

const LOCK_ICON = `<svg viewBox="0 0 24 24" fill="none" stroke="#ffd166" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>`;

export function mount(el, opts = {}) {
  injectStyle();
  const gsap = window.gsap;
  const media = opts.media || 'media/';
  const loop = opts.loop !== false;

  const root = document.createElement('div');
  root.className = 'anc';
  const stage = document.createElement('div');
  stage.className = 'anc-stage';
  root.appendChild(stage);
  el.appendChild(root);

  const add = (cls, html, style) => {
    const d = document.createElement('div');
    d.className = cls;
    if (html) d.innerHTML = html;
    if (style) Object.assign(d.style, style);
    stage.appendChild(d);
    return d;
  };

  const headBurst = add('anc-head', 'camera burst · <b>6 frames</b>', { left: `${GRID_X}px`, top: '70px' });
  const headMatch = add('anc-head', 'anchor match', { left: `${BIG.x}px`, top: '80px', width: `${BIG.w}px`, textAlign: 'center' });
  const headRef = add('anc-head', 'reference', { left: `${THUMB.x}px`, top: '80px', width: `${THUMB.w}px`, textAlign: 'center' });

  // burst tiles
  const keys = ['KL-A01', 'KL-A02', 'KL-A03', 'KL-A01', 'KL-A02', 'KL-A03'];
  const WIN = 2;
  const tiles = keys.map((k, i) => {
    const col = i % 2, row = (i / 2) | 0;
    const cv = cover(k, TILE_W, TILE_H);
    const t = add('anc-tile', `<span class="anc-idx">${String(i + 1).padStart(2, '0')}</span>`, {
      left: `${GRID_X + col * (TILE_W + GAP)}px`, top: `${GRID_Y + row * (TILE_H + GAP)}px`,
      width: `${TILE_W}px`, height: `${TILE_H}px`,
      backgroundImage: `url("${media}${k}.jpg")`, backgroundSize: cv.size, backgroundPosition: cv.pos,
    });
    t.dataset.left = GRID_X + col * (TILE_W + GAP);
    t.dataset.top = GRID_Y + row * (TILE_H + GAP);
    t.dataset.size = cv.size;
    t.dataset.pos = cv.pos;
    return t;
  });
  const win = tiles[WIN];
  const ghost = add('anc-ghost', '', {
    left: `${win.dataset.left}px`, top: `${win.dataset.top}px`, width: `${TILE_W}px`, height: `${TILE_H}px`, opacity: 0,
  });
  stage.insertBefore(ghost, tiles[0]);
  const winRing = document.createElement('div');
  winRing.className = 'anc-win-ring';
  win.appendChild(winRing);
  const winIdx = win.querySelector('.anc-idx');
  const [iw, ih] = IMG['KL-A03'];
  const bigSize = `${(iw * BIG_K).toFixed(1)}px ${(ih * BIG_K).toFixed(1)}px`;
  const bigPos = `0px ${-BIG_OY}px`;

  // reference thumbnail
  const thumb = add('anc-thumb', '', {
    left: `${THUMB.x}px`, top: `${THUMB.y}px`, width: `${THUMB.w}px`, height: `${THUMB.h}px`,
    backgroundImage: `url("${media}KL-A03.jpg")`,
    backgroundSize: `${iw * THUMB.k}px ${ih * THUMB.k}px`,
    backgroundPosition: `${-THUMB.sx * THUMB.k}px ${-THUMB.sy * THUMB.k}px`,
  });
  const thumbLabel = add('anc-thumb-label', '<b>KL-A03</b> · text anchor<br>‘1116W’', {
    left: `${THUMB.x - 40}px`, top: `${THUMB.y + THUMB.h + 14}px`, width: `${THUMB.w + 80}px`,
  });

  // canvas for points and match lines
  const canvas = document.createElement('canvas');
  canvas.className = 'anc-canvas';
  stage.appendChild(canvas);
  const ctx = canvas.getContext('2d');

  // reticle around the sign
  const [sx0, sy0] = toBig(SIGN.x0, SIGN.y0);
  const [sx1, sy1] = toBig(SIGN.x1, SIGN.y1);
  const RP = 12;
  const ret = add('anc-ret', '<i></i><i></i><i></i><i></i>', {
    left: `${sx0 - RP}px`, top: `${sy0 - RP}px`, width: `${sx1 - sx0 + 2 * RP}px`, height: `${sy1 - sy0 + 2 * RP}px`,
  });
  const lock = add('anc-lock', `${LOCK_ICON}<span>anchor locked · pose reset</span>`, {
    left: `${BIG.x - 100}px`, top: `${BIG.y + BIG.h + 34}px`, width: `${BIG.w + 200}px`,
  });
  const chipsWrap = add('anc-chips', '', { left: `${BIG.x - 200}px`, top: `${BIG.y + BIG.h + 88}px`, width: `${BIG.w + 400}px` });
  const chips = ['GPS → door', 'sign OCR', 'barometer → floor 3'].map((txt) => {
    const c = document.createElement('div');
    c.className = 'anc-chip';
    c.textContent = txt;
    chipsWrap.appendChild(c);
    return c;
  });

  const { pts, matches } = makeFeatures(opts.seed ?? 1116);
  const st = { pts: 0, gold: 0 };

  // ---- sizing ----
  let scale = 1, dpr = 1;
  function layout() {
    const w = root.clientWidth, h = root.clientHeight;
    if (!w || !h) return;
    scale = Math.min(w / W, h / H);
    stage.style.transform = `translate(${((w - W * scale) / 2).toFixed(1)}px, ${((h - H * scale) / 2).toFixed(1)}px) scale(${scale})`;
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(W * scale * dpr);
    canvas.height = Math.round(H * scale * dpr);
    draw();
  }
  const ro = new ResizeObserver(layout);
  ro.observe(root);

  function draw() {
    const k = scale * dpr;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.setTransform(k, 0, 0, k, 0, 0);
    // match lines
    for (const m of matches) {
      if (m.t <= 0) continue;
      const x0 = m.p.bx, y0 = m.p.by;
      const x = x0 + (m.tx - x0) * m.t, y = y0 + (m.ty - y0) * m.t;
      const grad = ctx.createLinearGradient(x0, y0, m.tx, m.ty);
      grad.addColorStop(0, 'rgba(122,208,224,0.85)');
      grad.addColorStop(1, 'rgba(255,209,102,0.85)');
      ctx.strokeStyle = grad;
      ctx.lineWidth = 1.3;
      ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x, y); ctx.stroke();
      if (m.t >= 1) {
        ctx.fillStyle = '#ffd166';
        ctx.beginPath(); ctx.arc(m.tx, m.ty, 2.6, 0, Math.PI * 2); ctx.fill();
      }
    }
    // feature points
    for (const p of pts) {
      if (p.a <= 0) continue;
      const gold = p.matched ? st.gold : 0;
      const r = Math.round(122 + (255 - 122) * gold), g = Math.round(208 + (209 - 208) * gold), b = Math.round(224 + (102 - 224) * gold);
      ctx.globalAlpha = p.a;
      ctx.strokeStyle = `rgba(${r},${g},${b},0.55)`;
      ctx.lineWidth = 1.2;
      ctx.beginPath(); ctx.arc(p.bx, p.by, 5.5 + (1 - p.a) * 6, 0, Math.PI * 2); ctx.stroke();
      ctx.fillStyle = `rgb(${r},${g},${b})`;
      ctx.beginPath(); ctx.arc(p.bx, p.by, 2.4, 0, Math.PI * 2); ctx.fill();
      ctx.globalAlpha = 1;
    }
  }

  // ---- timeline ----
  let tl = null;
  function build() {
    const t = gsap.timeline({ paused: true, repeat: loop ? -1 : 0, repeatDelay: 3, onUpdate: draw });
    // reset
    t.set(win, {
      left: +win.dataset.left, top: +win.dataset.top, width: TILE_W, height: TILE_H,
      backgroundSize: win.dataset.size, backgroundPosition: win.dataset.pos, zIndex: 1,
    }, 0);
    t.set(winRing, { opacity: 0 }, 0);
    t.set(ghost, { opacity: 0 }, 0);
    t.set(winIdx, { opacity: 0.85 }, 0);
    t.set(pts, { a: 0 }, 0);
    t.set(matches, { t: 0 }, 0);
    t.set(st, { gold: 0 }, 0);
    t.set([thumb, thumbLabel, headRef, headMatch], { opacity: 0 }, 0);
    t.set(ret, { opacity: 0, scale: 1.3, color: '#ffffff' }, 0);
    t.set(lock, { opacity: 0, y: 10 }, 0);
    t.set(chips, { color: 'rgba(255,255,255,0.55)', borderColor: 'rgba(255,255,255,0.22)', backgroundColor: 'rgba(255,255,255,0.03)', opacity: 0, y: 8 }, 0);

    // 1. burst flies in
    t.fromTo(headBurst, { opacity: 0, x: -20 }, { opacity: 1, x: 0, duration: 0.4 }, 0);
    t.fromTo(tiles, { x: 600, rotation: 12, opacity: 0 },
      { x: 0, rotation: 0, opacity: 1, duration: 0.75, ease: 'power3.out', stagger: 0.08 }, 0.1);
    // 2. pick the winner
    t.to(tiles.filter((_, i) => i !== WIN), { opacity: 0.3, duration: 0.4 }, 1.35);
    t.to(winRing, { opacity: 1, duration: 0.25 }, 1.35);
    t.to(winIdx, { opacity: 0, duration: 0.2 }, 1.6);
    t.set(win, { zIndex: 5 }, 1.6);
    t.to(win, {
      left: BIG.x, top: BIG.y, width: BIG.w, height: BIG.h,
      backgroundSize: bigSize, backgroundPosition: bigPos,
      duration: 0.95, ease: 'power3.inOut',
    }, 1.65);
    t.to(winRing, { opacity: 0, duration: 0.3 }, 2.4);
    t.to(ghost, { opacity: 1, duration: 0.3 }, 2.0);
    t.to(headMatch, { opacity: 1, duration: 0.4 }, 2.3);
    // 3. feature points wave
    t.to(pts, { a: 1, duration: 0.3, ease: 'power2.out', stagger: { each: 0.012, from: 'random' } }, 2.7);
    t.fromTo(thumb, { opacity: 0, x: 40 }, { opacity: 1, x: 0, duration: 0.5, ease: 'power2.out' }, 3.0);
    t.to([thumbLabel, headRef], { opacity: 1, duration: 0.4 }, 3.2);
    // 4. match lines
    t.to(matches, { t: 1, duration: 0.5, ease: 'power2.inOut', stagger: 0.04 }, 3.9);
    // 5. reticle snaps on
    t.to(ret, { opacity: 1, duration: 0.2 }, 5.3);
    t.to(ret, { scale: 1, duration: 0.7, ease: 'back.out(2.6)' }, 5.3);
    t.to(ret, { color: '#ffd166', duration: 0.25 }, 5.85);
    t.to(st, { gold: 1, duration: 0.4 }, 5.85);
    t.to(lock, { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out' }, 6.0);
    t.to(chips, { opacity: 1, y: 0, duration: 0.3, stagger: 0.1 }, 6.2);
    chips.forEach((c, i) => {
      t.to(c, { color: '#1b1614', borderColor: '#ffd166', backgroundColor: '#ffd166', duration: 0.25 }, 6.7 + i * 0.4);
    });
    t.to({}, { duration: 2.5 }, 8.0); // hold
    return t;
  }
  if (gsap) tl = build();
  layout();

  return {
    play() { if (tl) tl.restart(); },
    pause() { if (tl) tl.pause(); },
    destroy() {
      if (tl) { tl.kill(); tl = null; }
      ro.disconnect();
      root.remove();
    },
  };
}

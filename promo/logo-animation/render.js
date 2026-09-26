// CampusMaps logo reveal, transparent, 1920x1080 @ 60 fps.
// Geometry and timing are taken from app/src/main/java/com/campusmaps/ui/splash/SplashOverlay.kt,
// with the video-specific extras: viewfinder lock-on, scan sweep, glowing route trace,
// light sweep on the wordmark, tagline, hold, and an out.
const { createCanvas, GlobalFonts } = require('@napi-rs/canvas');
const fs = require('fs');
const path = require('path');

const FONT_DIR = 'C:/Users/rapha/CampusMaps/app/src/main/res/font';
GlobalFonts.registerFromPath(`${FONT_DIR}/sora_bold.ttf`, 'SoraBold');
GlobalFonts.registerFromPath(`${FONT_DIR}/sora_medium.ttf`, 'SoraMedium');

const W = 1920, H = 1080, FPS = 60;
const OUT = process.argv[2] || 'frames';
const BG = process.argv[3] || null; // e.g. "#00ff00" for green screen, "#3a2f2a" for a preview
const TOTAL_MS = 4000;

// ---- Timing (ms) -----------------------------------------------------------------------------
const T = {
  cornersIn: [80, 620],      // lock-on: corners snap in from wide with overshoot
  pulse: [560, 760],         // one brightness pulse when the frame locks
  sweep: [520, 1180],        // scan line top to bottom inside the viewfinder
  pin: [760, 1160],          // pin drops as the sweep passes its position
  chevron: [1050, 90, 220],  // start, stagger, fade (bottom to top)
  line: [1350, 1750],        // glowing trace from the dot up to the pin
  word: [1300, 1750],        // wordmark rise + fade in
  shine: [1750, 2350],       // light sweep across the wordmark
  tagline: [1750, 2150],
  out: [3450, 3950],         // everything scales up slightly and fades
};

// ---- Easing ----------------------------------------------------------------------------------
const clamp01 = (x) => Math.min(1, Math.max(0, x));
const phase = (t, a, b) => clamp01((t - a) / (b - a));
const easeOut = (x) => 1 - Math.pow(1 - x, 3);
const easeOutExpo = (x) => (x >= 1 ? 1 : 1 - Math.pow(2, -10 * x));
const easeIn = (x) => x * x * x;
const easeInOut = (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
function easeOutBack(x, c1 = 1.70158) { const c3 = c1 + 1, y = x - 1; return 1 + c3 * y * y * y + c1 * y * y; }
function bezier(p1x, p1y, p2x, p2y) { // cubic-bezier as CSS
  return (x) => {
    if (x <= 0) return 0; if (x >= 1) return 1;
    let lo = 0, hi = 1, t = x;
    for (let i = 0; i < 24; i++) {
      t = (lo + hi) / 2;
      const bx = 3 * (1 - t) * (1 - t) * t * p1x + 3 * (1 - t) * t * t * p2x + t * t * t;
      if (bx < x) lo = t; else hi = t;
    }
    return 3 * (1 - t) * (1 - t) * t * p1y + 3 * (1 - t) * t * t * p2y + t * t * t;
  };
}
const lockOn = bezier(0.18, 0.89, 0.32, 1.28);

// ---- Design (splash units, 310 x 302 frame) --------------------------------------------------
const FRAME_W = 310, FRAME_H = 302;
const CHEVRONS = [ // bottom to top: [lx, ly, tx, ty, rx, ry, width]
  [110, 284, 155, 264, 200, 284, 9],
  [122, 234, 155, 219, 188, 234, 7],
  [132, 194, 155, 183, 178, 194, 5.5],
  [140, 162, 155, 155, 170, 162, 4],
];
const PIN_SCALE = 3.2, PIN_TIP = [155, 90], DOT = [155, 144];
const WHITE = '#ffffff';
const CLAY = '#c67c4e';

// Layout on the 1080p canvas.
const ART_SCALE = 1.55;                // splash units -> px
const ART_X = W / 2 - (FRAME_W * ART_SCALE) / 2;
const ART_Y = 150;
const WORD_Y = ART_Y + FRAME_H * ART_SCALE + 130;
const WORD_SIZE = 104;
const TAG_Y = WORD_Y + 78;

function shadow(ctx, blur = 22, alpha = 0.45) {
  ctx.shadowColor = `rgba(0,0,0,${alpha})`;
  ctx.shadowBlur = blur;
  ctx.shadowOffsetX = 0;
  ctx.shadowOffsetY = 4;
}
function noShadow(ctx) { ctx.shadowColor = 'rgba(0,0,0,0)'; ctx.shadowBlur = 0; ctx.shadowOffsetY = 0; }

// One viewfinder corner: arm, quarter arc, arm. i = 0..3 clockwise from top-left.
function cornerPath(ctx, w, h, r, arm, i) {
  ctx.beginPath();
  switch (i) {
    case 0: ctx.moveTo(0, arm); ctx.lineTo(0, r); ctx.arc(r, r, r, Math.PI, 1.5 * Math.PI); ctx.lineTo(arm, 0); break;
    case 1: ctx.moveTo(w - arm, 0); ctx.lineTo(w - r, 0); ctx.arc(w - r, r, r, 1.5 * Math.PI, 2 * Math.PI); ctx.lineTo(w, arm); break;
    case 2: ctx.moveTo(w, h - arm); ctx.lineTo(w, h - r); ctx.arc(w - r, h - r, r, 0, 0.5 * Math.PI); ctx.lineTo(w - arm, h); break;
    case 3: ctx.moveTo(arm, h); ctx.lineTo(r, h); ctx.arc(r, h - r, r, 0.5 * Math.PI, Math.PI); ctx.lineTo(0, h - arm); break;
  }
}

// The logo pin in its 40-unit box (tip at 20,30; head centre 20,18, radius 7).
function pinPath(ctx) {
  ctx.beginPath();
  ctx.moveTo(20, 30);
  ctx.bezierCurveTo(20, 30, 13, 23.8, 13, 18);
  ctx.arc(20, 18, 7, Math.PI, 2 * Math.PI);
  ctx.bezierCurveTo(27, 23.8, 20, 30, 20, 30);
  ctx.closePath();
}

function drawArt(ctx, t) {
  ctx.save();
  ctx.translate(ART_X, ART_Y);
  ctx.scale(ART_SCALE, ART_SCALE);
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.strokeStyle = WHITE;
  ctx.fillStyle = WHITE;

  // --- Corners: lock-on. Start 40 % wider, slightly rotated and faint; snap in with overshoot.
  const c = phase(t, ...T.cornersIn);
  if (c > 0) {
    const k = lockOn(c);
    const spread = 1 + 0.4 * (1 - k);
    const rot = (1 - k) * 0.06;
    const pulse = Math.sin(Math.PI * phase(t, ...T.pulse));
    ctx.save();
    ctx.translate(FRAME_W / 2, FRAME_H / 2);
    ctx.rotate(rot);
    ctx.scale(spread, spread);
    ctx.translate(-FRAME_W / 2, -FRAME_H / 2);
    ctx.globalAlpha = easeOut(phase(c, 0, 0.35));
    ctx.lineWidth = 3.5 + 1.2 * pulse;
    shadow(ctx, 16 + 30 * pulse, 0.45);
    if (pulse > 0) { ctx.shadowColor = `rgba(255,255,255,${0.6 * pulse})`; }
    for (let i = 0; i < 4; i++) { cornerPath(ctx, FRAME_W, FRAME_H, 12, 46, i); ctx.stroke(); }
    noShadow(ctx);
    ctx.restore();
  }

  // --- Scan sweep: a bright line with a soft tail moves top to bottom inside the frame.
  const sw = phase(t, ...T.sweep);
  if (sw > 0 && sw < 1) {
    const y = -10 + (FRAME_H + 20) * easeInOut(sw);
    const fade = Math.sin(Math.PI * sw);
    ctx.save();
    ctx.beginPath(); ctx.rect(8, 8, FRAME_W - 16, FRAME_H - 16); ctx.clip();
    const g = ctx.createLinearGradient(0, y - 60, 0, y);
    g.addColorStop(0, 'rgba(255,255,255,0)');
    g.addColorStop(1, `rgba(255,255,255,${0.22 * fade})`);
    ctx.fillStyle = g;
    ctx.fillRect(8, y - 60, FRAME_W - 16, 60);
    ctx.globalAlpha = 0.9 * fade;
    ctx.lineWidth = 1.4;
    ctx.shadowColor = 'rgba(255,255,255,0.9)'; ctx.shadowBlur = 10;
    ctx.beginPath(); ctx.moveTo(14, y); ctx.lineTo(FRAME_W - 14, y); ctx.stroke();
    ctx.restore();
    ctx.fillStyle = WHITE;
  }

  // --- Pin drops in with an overshoot, anticipation rise first.
  const p = phase(t, ...T.pin);
  if (p > 0) {
    const drop = (1 - easeOutBack(p)) * -70;
    const a = phase(t, T.pin[0], T.pin[0] + 120);
    ctx.save();
    ctx.globalAlpha = a;
    ctx.translate(PIN_TIP[0] - 20 * PIN_SCALE, PIN_TIP[1] - 30 * PIN_SCALE + drop);
    ctx.scale(PIN_SCALE, PIN_SCALE);
    ctx.lineWidth = 3;
    shadow(ctx, 18 / PIN_SCALE, 0.45);
    // ghost trails during the fast part of the drop
    if (p < 0.5) {
      for (const [dp, ga] of [[-0.06, 0.22], [-0.12, 0.1]]) {
        const pp = clamp01(p + dp); const gd = (1 - easeOutBack(pp)) * -70;
        ctx.save(); ctx.globalAlpha = a * ga; ctx.translate(0, (gd - drop) / PIN_SCALE);
        pinPath(ctx); ctx.stroke(); ctx.restore();
      }
    }
    pinPath(ctx); ctx.stroke();
    ctx.beginPath(); ctx.arc(20, 18, 2.5, 0, 2 * Math.PI); ctx.stroke();
    // landing ring when it touches down
    const land = phase(t, T.pin[0] + 200, T.pin[0] + 520);
    if (land > 0 && land < 1) {
      noShadow(ctx);
      ctx.globalAlpha = (1 - land) * 0.6;
      ctx.lineWidth = 1.2;
      ctx.beginPath(); ctx.ellipse(20, 30.5, 4 + 14 * easeOut(land), (4 + 14 * easeOut(land)) * 0.35, 0, 0, 2 * Math.PI); ctx.stroke();
    }
    ctx.restore();
  }

  // --- Chevrons fade in bottom to top, each rising a little.
  CHEVRONS.forEach((cv, i) => {
    const s = T.chevron[0] + i * T.chevron[1];
    const a = phase(t, s, s + T.chevron[2]);
    if (a <= 0) return;
    const dy = (1 - easeOut(a)) * 12;
    ctx.save();
    ctx.globalAlpha = 0.95 * a;
    ctx.lineWidth = cv[6];
    shadow(ctx, 14, 0.4);
    ctx.beginPath(); ctx.moveTo(cv[0], cv[1] + dy); ctx.lineTo(cv[2], cv[3] + dy); ctx.lineTo(cv[4], cv[5] + dy); ctx.stroke();
    ctx.restore();
  });

  // --- Dot, then the dashed route line traces up to the pin with a glowing head.
  const l = phase(t, ...T.line);
  if (l > 0) {
    ctx.save();
    shadow(ctx, 12, 0.4);
    ctx.beginPath(); ctx.arc(DOT[0], DOT[1], 5 * Math.max(0, easeOutBack(clamp01(l * 1.6))), 0, 2 * Math.PI); ctx.fill();
    const len = 46 * easeOut(l);
    ctx.lineWidth = 2;
    ctx.setLineDash([3, 4]);
    ctx.beginPath(); ctx.moveTo(DOT[0], DOT[1] - 4); ctx.lineTo(DOT[0], DOT[1] - 4 - len); ctx.stroke();
    ctx.setLineDash([]);
    if (l < 1) {
      noShadow(ctx);
      ctx.shadowColor = 'rgba(255,255,255,1)'; ctx.shadowBlur = 14;
      ctx.beginPath(); ctx.arc(DOT[0], DOT[1] - 4 - len, 2.2, 0, 2 * Math.PI); ctx.fill();
    }
    ctx.restore();
  }
  ctx.restore();
}

function drawWord(ctx, t) {
  const w = phase(t, ...T.word);
  if (w <= 0) return;
  const k = easeOutExpo(w);
  ctx.save();
  ctx.textAlign = 'center';
  ctx.textBaseline = 'alphabetic';
  ctx.globalAlpha = k;
  ctx.font = `${WORD_SIZE}px SoraBold`;
  ctx.letterSpacing = `${(1 - k) * 8 - 2}px`;
  const y = WORD_Y + (1 - k) * 28;
  shadow(ctx, 26, 0.5);
  ctx.fillStyle = WHITE;
  ctx.fillText('CampusMaps', W / 2, y);
  noShadow(ctx);

  // Light sweep: a skewed bright band, clipped to the glyphs.
  const s = phase(t, ...T.shine);
  if (s > 0 && s < 1) {
    const tw = ctx.measureText('CampusMaps').width;
    const x0 = W / 2 - tw / 2 - 200 + (tw + 400) * easeInOut(s);
    ctx.globalCompositeOperation = 'source-atop';
    ctx.save();
    ctx.translate(x0, y);
    ctx.transform(1, 0, -0.35, 1, 0, 0);
    const g = ctx.createLinearGradient(-70, 0, 70, 0);
    g.addColorStop(0, 'rgba(255,255,255,0)');
    g.addColorStop(0.5, `rgba(255,236,220,${0.9 * Math.sin(Math.PI * s)})`);
    g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g;
    ctx.fillRect(-70, -WORD_SIZE - 10, 140, WORD_SIZE + 40);
    ctx.restore();
    ctx.globalCompositeOperation = 'source-over';
  }
  ctx.restore();

  const tg = phase(t, ...T.tagline);
  if (tg > 0) {
    const kk = easeOutExpo(tg);
    ctx.save();
    ctx.textAlign = 'center';
    ctx.globalAlpha = kk;
    ctx.font = '30px SoraMedium';
    ctx.letterSpacing = `${(1 - kk) * 6 + 5}px`;
    ctx.fillStyle = CLAY;
    shadow(ctx, 18, 0.55);
    ctx.fillText('INDOOR AR NAVIGATION', W / 2, TAG_Y + (1 - kk) * 14);
    ctx.restore();
  }
}

function drawFrame(ctx, t) {
  ctx.clearRect(0, 0, W, H);
  if (BG) { ctx.fillStyle = BG; ctx.fillRect(0, 0, W, H); }
  const o = phase(t, ...T.out);
  const oe = easeIn(o);
  ctx.save();
  if (o > 0) {
    ctx.globalAlpha = 1 - oe;
    const sc = 1 + 0.06 * easeOut(o);
    ctx.translate(W / 2, H / 2); ctx.scale(sc, sc); ctx.translate(-W / 2, -H / 2);
  }
  // Group everything on a layer so the out-fade does not double-blend overlapping strokes.
  drawArt(ctx, t);
  drawWord(ctx, t);
  ctx.restore();
}

const canvas = createCanvas(W, H);
const ctx = canvas.getContext('2d');
fs.mkdirSync(OUT, { recursive: true });
const frames = Math.round((TOTAL_MS / 1000) * FPS);
for (let i = 0; i < frames; i++) {
  const t = (i / FPS) * 1000;
  // Out-fade needs a layer: render into an offscreen and composite with alpha.
  const off = createCanvas(W, H), oc = off.getContext('2d');
  const o = phase(t, ...T.out);
  drawArt(oc, t); drawWord(oc, t);
  ctx.clearRect(0, 0, W, H);
  if (BG) { ctx.fillStyle = BG; ctx.fillRect(0, 0, W, H); }
  ctx.save();
  ctx.globalAlpha = 1 - easeIn(o);
  const sc = 1 + 0.06 * easeOut(o);
  ctx.translate(W / 2, H / 2); ctx.scale(sc, sc); ctx.translate(-W / 2, -H / 2);
  ctx.drawImage(off, 0, 0);
  ctx.restore();
  fs.writeFileSync(path.join(OUT, `f_${String(i).padStart(5, '0')}.png`), canvas.toBuffer('image/png'));
  if (i % 60 === 0) console.log(`frame ${i}/${frames}`);
}
console.log('done', frames, 'frames');

// haptics.js: "the wrist buzzes each turn". Eight mini watch faces, each with
// its real vibration pattern drawn as a waveform that lights up in real time.
// Contract: mount(el, opts) -> { play(), pause(), destroy() }

const STYLE_ID = 'haptics-style';
const CSS = `
.hap{width:100%;height:100%;container-type:inline-size;font-family:'Sora',system-ui,sans-serif;color:#fff;
  display:flex;align-items:center;justify-content:center;background:transparent}
.hap *{box-sizing:border-box}
.hap-grid{display:grid;grid-template-columns:repeat(8,minmax(0,150px));gap:14px;justify-content:center;width:100%}
@container (max-width:1100px){.hap-grid{grid-template-columns:repeat(4,minmax(0,170px));gap:16px 18px}}
.hap-card{position:relative;display:flex;flex-direction:column;align-items:center;gap:8px;padding:16px 8px 14px;
  border-radius:22px;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.09)}
.hap-card .hap-glow{position:absolute;inset:-1px;border-radius:22px;border:2px solid var(--c);opacity:0;pointer-events:none;
  box-shadow:0 0 24px -4px var(--c)}
.hap-watch{width:104px;height:104px;overflow:visible;display:block}
.hap-name{font-weight:700;font-size:17px;letter-spacing:-.01em;margin-top:2px}
.hap-desc{font-weight:500;font-size:12.5px;color:rgba(255,255,255,.6);margin-top:-5px}
.hap-wave{width:100%;max-width:134px;height:30px;display:block;overflow:visible}
.hap-ms{font-weight:500;font-size:11.5px;color:rgba(255,255,255,.45);font-variant-numeric:tabular-nums}
`;

function injectStyle() {
  if (document.getElementById(STYLE_ID)) return;
  const s = document.createElement('style');
  s.id = STYLE_ID;
  s.textContent = CSS;
  document.head.appendChild(s);
}

const TEAL = '#7ad0e0', WARM = '#ffb68c', AMBER = '#ffb020', GOLD = '#ffd166', CLAY = '#c67c4e';

export const PATTERNS = [
  { id: 'straight', name: 'Straight', desc: '1 tap', p: [80], c: TEAL,
    icon: '<path d="M52 72V34M40 46l12-12 12 12"/>' },
  { id: 'left', name: 'Turn left', desc: '2 taps', p: [80, 80, 80], c: TEAL,
    icon: '<path d="M62 73V53a10 10 0 0 0-10-10H37"/><path d="M46 34l-9 9 9 9"/>' },
  { id: 'right', name: 'Turn right', desc: '5 taps', p: [80, 80, 80, 80, 80, 80, 80, 80, 80], c: TEAL,
    icon: '<path d="M42 73V53a10 10 0 0 1 10-10h15"/><path d="M58 34l9 9-9 9"/>' },
  { id: 'stairs', name: 'Stairs', desc: 'long · tap', p: [300, 100, 80], c: WARM,
    icon: '<path d="M33 70h11V60h11V50h11V40h6"/>' },
  { id: 'elevator', name: 'Elevator', desc: 'long · long', p: [300, 100, 300], c: WARM,
    icon: '<rect x="38" y="31" width="28" height="42" rx="4"/><path d="M46 47l6-6 6 6M46 57l6 6 6-6"/>' },
  { id: 'door', name: 'Door', desc: 'tap · long', p: [80, 100, 300], c: WARM,
    icon: '<rect x="40" y="30" width="24" height="42" rx="2"/><path d="M33 73h38"/><circle cx="58" cy="52" r="1.6" fill="currentColor"/>' },
  { id: 'locked', name: 'Locked', desc: '4 fast taps', p: [80, 60, 80, 60, 80, 60, 80, 60], c: AMBER,
    icon: '<rect x="39" y="48" width="26" height="22" rx="4"/><path d="M45 48v-6a7 7 0 0 1 14 0v6"/><path d="M52 56v6"/>' },
  { id: 'arrive', name: 'Arrived', desc: 'one long buzz', p: [600], c: GOLD,
    icon: '<path d="M52 74S36 59 36 47a16 16 0 0 1 32 0c0 12-16 27-16 27z"/><circle cx="52" cy="47" r="5"/>' },
];

const SVGNS = 'http://www.w3.org/2000/svg';
const WAVE_W = 134, WAVE_H = 30;
const MAX_MS = Math.max(...PATTERNS.map((x) => x.p.reduce((a, b) => a + b, 0)));
const DIM = 'rgba(255,255,255,0.16)';

function watchSvg(pat) {
  let ticks = '';
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2;
    const r1 = 47.5, r2 = i % 3 === 0 ? 44 : 45.5;
    ticks += `<line x1="${52 + r1 * Math.sin(a)}" y1="${52 - r1 * Math.cos(a)}" x2="${52 + r2 * Math.sin(a)}" y2="${52 - r2 * Math.cos(a)}"/>`;
  }
  return `<svg class="hap-watch" viewBox="0 0 104 104" style="color:${pat.c}">
    <circle class="hap-ring" cx="52" cy="52" r="46" fill="none" stroke="${pat.c}" stroke-width="3" opacity="0"/>
    <g class="hap-face">
      <circle cx="52" cy="52" r="50" fill="#2a221f" stroke="#4a3d36" stroke-width="1.5"/>
      <g stroke="rgba(255,255,255,.35)" stroke-width="1.4" stroke-linecap="round">${ticks}</g>
      <circle class="hap-dial" cx="52" cy="52" r="41" fill="#0d0b0a"/>
      <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">${pat.icon}</g>
    </g>
  </svg>`;
}

function waveSvg(pat) {
  const k = WAVE_W / MAX_MS;
  const svg = document.createElementNS(SVGNS, 'svg');
  svg.setAttribute('class', 'hap-wave');
  svg.setAttribute('viewBox', `0 0 ${WAVE_W} ${WAVE_H}`);
  svg.setAttribute('preserveAspectRatio', 'xMinYMid meet');
  const base = document.createElementNS(SVGNS, 'line');
  base.setAttribute('x1', 0); base.setAttribute('x2', WAVE_W);
  base.setAttribute('y1', WAVE_H / 2); base.setAttribute('y2', WAVE_H / 2);
  base.setAttribute('stroke', 'rgba(255,255,255,0.12)');
  base.setAttribute('stroke-width', '1.5');
  base.setAttribute('stroke-dasharray', '2 3');
  svg.appendChild(base);
  const segs = [];
  let t = 0;
  pat.p.forEach((ms, i) => {
    if (i % 2 === 0) {
      const r = document.createElementNS(SVGNS, 'rect');
      r.setAttribute('x', (t * k).toFixed(2));
      r.setAttribute('y', 3);
      r.setAttribute('width', Math.max(2, ms * k - 1).toFixed(2));
      r.setAttribute('height', WAVE_H - 6);
      r.setAttribute('rx', 2.5);
      r.style.fill = DIM;
      svg.appendChild(r);
      segs.push({ el: r, start: t, dur: ms });
    }
    t += ms;
  });
  const head = document.createElementNS(SVGNS, 'line');
  head.setAttribute('y1', 0); head.setAttribute('y2', WAVE_H);
  head.setAttribute('x1', 0); head.setAttribute('x2', 0);
  head.setAttribute('stroke', '#fff');
  head.setAttribute('stroke-width', '1.5');
  head.style.opacity = 0;
  svg.appendChild(head);
  return { svg, segs, head, total: t, width: t * k };
}

export function mount(el, opts = {}) {
  injectStyle();
  const gsap = window.gsap;
  const gapBetween = opts.gap ?? 0.5; // s between cards firing
  const hold = opts.hold ?? 1; // s hold before looping

  const root = document.createElement('div');
  root.className = 'hap';
  const grid = document.createElement('div');
  grid.className = 'hap-grid';
  root.appendChild(grid);
  el.appendChild(root);

  const cards = PATTERNS.map((pat) => {
    const card = document.createElement('div');
    card.className = 'hap-card';
    card.style.setProperty('--c', pat.c);
    card.innerHTML = `<div class="hap-glow"></div>${watchSvg(pat)}<div class="hap-name">${pat.name}</div><div class="hap-desc">${pat.desc}</div>`;
    const w = waveSvg(pat);
    card.appendChild(w.svg);
    const ms = document.createElement('div');
    ms.className = 'hap-ms';
    const pulses = Math.ceil(pat.p.length / 2);
    ms.textContent = pat.p.length > 3
      ? `${pulses} × ${pat.p[0]} on / ${pat.p[1]} off`
      : `${pat.p.join(' · ')} ms`;
    card.appendChild(ms);
    grid.appendChild(card);
    return {
      pat, card, wave: w,
      glow: card.querySelector('.hap-glow'),
      ring: card.querySelector('.hap-ring'),
      face: card.querySelector('.hap-face'),
      dial: card.querySelector('.hap-dial'),
    };
  });

  let master = null;
  function build() {
    const m = gsap.timeline({ paused: true });
    const els = cards.map((c) => c.card);
    m.fromTo(els, { y: 40, opacity: 0, scale: 0.92 },
      { y: 0, opacity: 1, scale: 1, duration: 0.6, ease: 'back.out(1.6)', stagger: 0.07 }, 0);

    const loop = gsap.timeline({ repeat: -1, repeatDelay: hold });
    // reset state at the top of every iteration
    cards.forEach((c) => {
      loop.set(c.wave.segs.map((s) => s.el), { fill: DIM }, 0);
      loop.set(c.glow, { opacity: 0 }, 0);
      loop.set(c.dial, { attr: { fill: '#0d0b0a' } }, 0);
    });
    let t = 0.15;
    cards.forEach((c) => {
      const total = c.wave.total / 1000;
      const col = c.pat.c;
      loop.to(c.glow, { opacity: 1, duration: 0.12 }, t);
      loop.to(c.glow, { opacity: 0.25, duration: 0.5 }, t + total + 0.1);
      loop.fromTo(c.ring, { scale: 1, opacity: 0.9, svgOrigin: '52 52' },
        { scale: 1.3, opacity: 0, duration: 0.55, ease: 'power2.out', svgOrigin: '52 52' }, t);
      loop.fromTo(c.wave.head, { attr: { x1: 0, x2: 0 }, opacity: 1 },
        { attr: { x1: c.wave.width, x2: c.wave.width }, duration: total, ease: 'none' }, t);
      loop.to(c.wave.head, { opacity: 0, duration: 0.15 }, t + total);
      c.wave.segs.forEach((s) => {
        const st = t + s.start / 1000, d = s.dur / 1000;
        loop.to(s.el, { fill: col, duration: 0.01, ease: 'none' }, st);
        loop.to(s.el, { fill: 'rgba(255,255,255,0.42)', duration: 0.3 }, st + d);
        loop.to(c.dial, { attr: { fill: '#1d2a2d' }, duration: 0.01 }, st);
        loop.to(c.dial, { attr: { fill: '#0d0b0a' }, duration: 0.12 }, st + d);
        const n = 2 * Math.max(1, Math.floor(s.dur / 40)) - 1;
        loop.fromTo(c.face, { x: -1.4 }, { x: 1.4, duration: d / (n + 1), repeat: n, yoyo: true, ease: 'none' }, st);
        loop.set(c.face, { x: 0 }, st + d);
      });
      t += total + gapBetween;
    });
    loop.to({}, { duration: 0.01 }, t);
    m.add(loop, 1.0);
    return m;
  }

  if (gsap) master = build();

  return {
    play() { if (master) master.restart(); },
    pause() { if (master) master.pause(); },
    destroy() {
      if (master) { master.kill(); master = null; }
      root.remove();
    },
  };
}

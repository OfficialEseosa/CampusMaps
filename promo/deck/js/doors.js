// Problem slide: a building in plan view with several doors. The "as the crow flies" line goes
// to the nearest door and dead-ends at a locked entrance; the real route uses another door,
// the elevator, and arrives. Pure SVG + GSAP.
const NS = 'http://www.w3.org/2000/svg';
const el = (tag, attrs = {}, parent) => {
  const n = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
  if (parent) parent.appendChild(n);
  return n;
};

export function doorsModule(root) {
  root.innerHTML = '';
  const svg = el('svg', { viewBox: '0 0 1400 560', style: 'width:100%;height:100%;font-family:Sora,sans-serif' }, root);

  // Building footprint (Classroom South-ish): two wings.
  const bld = el('g', { id: 'bld' }, svg);
  el('rect', { x: 420, y: 60, width: 860, height: 440, rx: 14, fill: '#251e1b', stroke: 'rgba(255,255,255,.18)', 'stroke-width': 2 }, bld);
  // Interior: corridor, rooms.
  el('rect', { x: 470, y: 250, width: 760, height: 60, fill: 'rgba(255,255,255,.05)' }, bld);
  el('rect', { x: 840, y: 100, width: 60, height: 360, fill: 'rgba(255,255,255,.05)' }, bld);
  const rooms = [[480, 100], [600, 100], [720, 100], [960, 100], [1080, 100], [480, 340], [600, 340], [720, 340], [960, 340]];
  rooms.forEach(([x, y]) => el('rect', { x, y, width: 100, height: 120, fill: 'none', stroke: 'rgba(255,255,255,.12)' }, bld));
  const dest = el('rect', { x: 1080, y: 340, width: 150, height: 120, fill: 'rgba(255,182,140,.12)', stroke: '#ffb68c', 'stroke-width': 2 }, bld);
  el('text', { x: 1155, y: 410, fill: '#ffb68c', 'font-size': 26, 'font-weight': 700, 'text-anchor': 'middle' }, bld).textContent = '608';
  // Elevator
  el('rect', { x: 850, y: 255, width: 40, height: 50, fill: 'rgba(122,208,224,.18)', stroke: '#7ad0e0' }, bld);
  el('text', { x: 870, y: 335, fill: '#7ad0e0', 'font-size': 16, 'text-anchor': 'middle' }, bld).textContent = 'elevator';

  // Doors along the west/south faces.
  const doors = [
    { x: 420, y: 150, name: 'Main', locked: true },
    { x: 420, y: 280, name: 'West', locked: false },
    { x: 700, y: 500, name: 'South', locked: true },
    { x: 1000, y: 500, name: 'Library', locked: false },
    { x: 1280, y: 200, name: 'East', locked: true },
  ];
  const doorG = el('g', {}, svg);
  doors.forEach(d => {
    const g = el('g', { class: 'door', transform: `translate(${d.x},${d.y})` }, doorG);
    el('circle', { r: 13, fill: '#1b1614', stroke: '#fff', 'stroke-width': 3 }, g);
    const lock = el('g', { class: 'lock', opacity: 0 }, g);
    el('circle', { r: 13, fill: '#e05a4e' }, lock);
    el('rect', { x: -5, y: -2, width: 10, height: 8, rx: 1.5, fill: '#fff' }, lock);
    el('path', { d: 'M-3 -2 v-3 a3 3 0 0 1 6 0 v3', fill: 'none', stroke: '#fff', 'stroke-width': 1.8 }, lock);
    const dx = d.x < 500 ? -22 : d.x > 1200 ? 22 : 0, dy = d.y > 480 ? 34 : d.y === 280 ? -22 : 6;
    el('text', { x: dx, y: dy, fill: 'rgba(255,248,244,.75)', 'font-size': 17, 'text-anchor': dx < 0 ? 'end' : dx > 0 ? 'start' : 'middle' }, g).textContent = d.name;
    d.lockEl = lock;
  });

  // You.
  const you = el('g', { transform: 'translate(150,330)' }, svg);
  el('circle', { r: 16, fill: '#c67c4e' }, you);
  el('circle', { r: 26, fill: 'none', stroke: '#c67c4e', 'stroke-width': 2, opacity: .5, class: 'ring' }, you);
  el('text', { x: 0, y: 58, fill: 'rgba(255,248,244,.75)', 'font-size': 18, 'text-anchor': 'middle' }, you).textContent = 'you';

  // Naive line: straight to the nearest door (Main), then a dead end.
  const naive = el('path', { d: 'M150 330 L420 150', fill: 'none', stroke: 'rgba(255,255,255,.55)', 'stroke-width': 4, 'stroke-dasharray': '10 12', 'stroke-linecap': 'round' }, svg);
  const naiveX = el('text', { x: 300, y: 215, fill: 'rgba(255,255,255,.7)', 'font-size': 20, 'text-anchor': 'middle', opacity: 0 }, svg);
  naiveX.textContent = '“closest door” — card-only after 8 PM';
  // Real route: West door, corridor, elevator, up, 608.
  const real = el('path', { d: 'M150 330 C 250 330, 330 285, 420 280 L 500 280 L 870 280 L 870 300 L 870 380 L 1000 380 L 1080 380 L 1080 400', fill: 'none', stroke: '#c67c4e', 'stroke-width': 8, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, svg);
  const head = el('circle', { r: 9, fill: '#fff', opacity: 0 }, svg);
  const eta = el('text', { x: 1030, y: 330, fill: '#c67c4e', 'font-size': 28, 'font-weight': 700, 'text-anchor': 'middle', opacity: 0 }, svg);
  eta.textContent = '1:27';
  el('text', { x: 420, y: 545, fill: 'rgba(255,248,244,.6)', 'font-size': 20, 'text-anchor': 'start' }, svg).textContent = 'Classroom South, plan view (simplified)';

  const rl = real.getTotalLength(), nl = naive.getTotalLength();
  real.style.strokeDasharray = rl; naive.style.strokeDasharray = '10 12';
  let tl;
  function build() {
    tl?.kill();
    gsap.set(real, { strokeDashoffset: rl });
    gsap.set(naive, { strokeDasharray: `${nl} ${nl}`, strokeDashoffset: nl });
    gsap.set([naiveX, eta, head], { opacity: 0 });
    doors.forEach(d => gsap.set(d.lockEl, { opacity: 0, scale: 0.6, transformOrigin: 'center' }));
    gsap.set('.door', { opacity: 0, scale: 0.5, transformOrigin: 'center' });
    gsap.set(bld, { opacity: 0, y: 20 });
    tl = gsap.timeline()
      .to(bld, { opacity: 1, y: 0, duration: .7, ease: 'power3.out' })
      .to('.door', { opacity: 1, scale: 1, duration: .5, stagger: .08, ease: 'back.out(2)' }, '-=.3')
      .to(naive, { strokeDashoffset: 0, duration: .9, ease: 'power2.inOut' }, '+=.2')
      .to(doors[0].lockEl, { opacity: 1, scale: 1, duration: .35, ease: 'back.out(3)' })
      .to(naiveX, { opacity: 1, duration: .4 }, '<')
      .to(naive, { stroke: '#e05a4e', duration: .3 }, '<')
      .to([doors[2].lockEl, doors[4].lockEl], { opacity: 1, scale: 1, duration: .35, stagger: .15, ease: 'back.out(3)' }, '+=.2')
      .to(head, { opacity: 1, duration: .2 }, '+=.4')
      .to(real, { strokeDashoffset: 0, duration: 2.6, ease: 'power1.inOut',
        onUpdate() { const p = real.getPointAtLength(rl * this.progress()); head.setAttribute('cx', p.x); head.setAttribute('cy', p.y); } }, '<')
      .to(head, { opacity: 0, duration: .2 })
      .to(eta, { opacity: 1, y: -6, duration: .5, ease: 'power3.out' }, '<')
      .to(dest, { fill: 'rgba(255,182,140,.35)', duration: .4, yoyo: true, repeat: 3 }, '<');
    gsap.to('.ring', { attr: { r: 44 }, opacity: 0, duration: 1.6, repeat: -1, ease: 'power1.out' });
  }
  return {
    play() { build(); },
    pause() { tl?.pause(); },
    destroy() { tl?.kill(); root.innerHTML = ''; },
  };
}

// armock.js: phone frame with the real AR screen recording and a Three.js
// floor path (flowing chevrons, amber turn arrow at the bend, ground reticle).
// Contract: mount(el, opts) -> { play(), pause(), destroy() }
import * as THREE from 'three';

const STYLE_ID = 'armock-style';
const CSS = `
.armock{position:relative;width:100%;height:100%;display:flex;flex-direction:column;align-items:center;
  gap:14px;font-family:'Sora',system-ui,sans-serif;color:#fff;background:transparent;box-sizing:border-box}
.armock *{box-sizing:border-box}
.armock-label{flex:0 0 auto;display:flex;align-items:center;gap:12px;height:56px;padding:0 20px 0 10px;
  border-radius:28px;background:rgba(27,22,20,.9);border:1px solid rgba(255,182,140,.35);
  box-shadow:0 8px 24px rgba(0,0,0,.35);font-weight:700;font-size:21px;letter-spacing:-.01em;white-space:nowrap}
.armock-ico{width:38px;height:38px;border-radius:50%;background:#ffb020;display:grid;place-items:center;flex:0 0 auto}
.armock-ico svg{width:24px;height:24px}
.armock-text{display:inline-block;min-width:0}
.armock-num{color:#ffb68c;display:inline-block;min-width:.7em;text-align:center}
.armock-phone{position:relative;flex:0 0 auto;border-radius:48px;border:2px solid rgba(255,255,255,.2);
  background:#0b0908;padding:9px;box-shadow:0 0 0 6px #14100e,0 24px 60px rgba(0,0,0,.55)}
.armock-screen{position:absolute;inset:9px;border-radius:39px;overflow:hidden;
  background:radial-gradient(120% 80% at 50% 20%,#3a2e28 0%,#1f1916 55%,#0f0c0b 100%)}
.armock-screen video{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;display:block}
.armock-screen canvas{position:absolute;inset:0;width:100%;height:100%;display:block}
.armock-hole{position:absolute;top:16px;left:50%;width:13px;height:13px;margin-left:-6.5px;border-radius:50%;
  background:#050404;box-shadow:0 0 0 2px rgba(255,255,255,.06);z-index:3}
.armock-fail video{display:none}
`;

function injectStyle() {
  if (document.getElementById(STYLE_ID)) return;
  const s = document.createElement('style');
  s.id = STYLE_ID;
  s.textContent = CSS;
  document.head.appendChild(s);
}

const ICON_LEFT = `<svg viewBox="0 0 24 24" fill="none" stroke="#1b1614" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><path d="M16 20v-7a3 3 0 0 0-3-3H6"/><path d="M10 6l-4 4 4 4"/></svg>`;
const ICON_ROOM = `<svg viewBox="0 0 24 24" fill="none" stroke="#1b1614" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><rect x="7" y="3.5" width="10" height="17" rx="1.5"/><circle cx="14" cy="12.5" r="1" fill="#1b1614"/></svg>`;

// White chevrons on a faint teal band. Canvas "up" = forward along the path.
function makeChevronTexture() {
  const c = document.createElement('canvas');
  c.width = 128; c.height = 256;
  const g = c.getContext('2d');
  g.clearRect(0, 0, 128, 256);
  // band
  const band = g.createLinearGradient(0, 0, 128, 0);
  band.addColorStop(0, 'rgba(122,208,224,0.0)');
  band.addColorStop(0.12, 'rgba(122,208,224,0.55)');
  band.addColorStop(0.2, 'rgba(122,208,224,0.22)');
  band.addColorStop(0.8, 'rgba(122,208,224,0.22)');
  band.addColorStop(0.88, 'rgba(122,208,224,0.55)');
  band.addColorStop(1, 'rgba(122,208,224,0.0)');
  g.fillStyle = band;
  g.fillRect(0, 0, 128, 256);
  // two chevrons per tile
  g.lineCap = 'round';
  g.lineJoin = 'round';
  for (const cy of [64, 192]) {
    g.strokeStyle = 'rgba(0,0,0,0.25)';
    g.lineWidth = 17;
    g.beginPath(); g.moveTo(38, cy + 24); g.lineTo(64, cy - 6); g.lineTo(90, cy + 24); g.stroke();
    g.strokeStyle = '#ffffff';
    g.lineWidth = 12;
    g.beginPath(); g.moveTo(38, cy + 20); g.lineTo(64, cy - 10); g.lineTo(90, cy + 20); g.stroke();
  }
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.wrapS = THREE.ClampToEdgeWrapping;
  t.wrapT = THREE.RepeatWrapping;
  t.repeat.set(1, 8);
  return t;
}

// TubeGeometry flattened onto the floor, with UVs remapped to
// u = across the path (0..1), v = along the path (0..1).
function makeFlatTube(curve, radius, tubular, radial) {
  const geo = new THREE.TubeGeometry(curve, tubular, radius, radial, false);
  const pos = geo.attributes.position;
  const uv = geo.attributes.uv;
  const up = new THREE.Vector3(0, 1, 0);
  const P = new THREE.Vector3(), T = new THREE.Vector3(), S = new THREE.Vector3(), V = new THREE.Vector3(), D = new THREE.Vector3();
  for (let i = 0; i <= tubular; i++) {
    const t = i / tubular;
    curve.getPointAt(t, P);
    curve.getTangentAt(t, T);
    S.crossVectors(T, up).normalize(); // right-hand side of the path
    for (let j = 0; j <= radial; j++) {
      const k = i * (radial + 1) + j;
      V.fromBufferAttribute(pos, k);
      D.subVectors(V, P);
      const lateral = THREE.MathUtils.clamp(D.dot(S), -radius, radius);
      const vert = D.dot(up);
      const y = 0.012 + (vert >= 0 ? 0.004 : -0.004);
      pos.setXYZ(k, P.x + S.x * lateral, y, P.z + S.z * lateral);
      uv.setXY(k, 0.5 + lateral / (2 * radius), t);
    }
  }
  pos.needsUpdate = true;
  uv.needsUpdate = true;
  geo.computeVertexNormals();
  return geo;
}

function makeTurnArrow() {
  const s = new THREE.Shape();
  s.moveTo(-0.42, 0);
  s.lineTo(-0.04, 0.32);
  s.lineTo(-0.04, 0.13);
  s.lineTo(0.4, 0.13);
  s.lineTo(0.4, -0.13);
  s.lineTo(-0.04, -0.13);
  s.lineTo(-0.04, -0.32);
  s.closePath();
  const geo = new THREE.ExtrudeGeometry(s, { depth: 0.08, bevelEnabled: true, bevelThickness: 0.015, bevelSize: 0.015, bevelSegments: 2 });
  geo.translate(0, 0, -0.04);
  const front = new THREE.MeshBasicMaterial({ color: 0xffb020 });
  const side = new THREE.MeshBasicMaterial({ color: 0xa8640c });
  const mesh = new THREE.Mesh(geo, [front, side]);
  // soft halo behind it
  const halo = new THREE.Mesh(
    new THREE.CircleGeometry(0.62, 48),
    new THREE.MeshBasicMaterial({ color: 0xffb020, transparent: true, opacity: 0.18, depthWrite: false })
  );
  halo.position.z = -0.08;
  const g = new THREE.Group();
  g.add(halo, mesh);
  return { group: g, halo, materials: [front, side, halo.material], geometries: [geo, halo.geometry] };
}

export function mount(el, opts = {}) {
  injectStyle();
  const videoSrc = opts.video || 'media/ar-walk.mp4';
  // overlay: false plays the recording clean (no 3D chevrons, no label),
  // for recordings that already show the app's own arrows and banners.
  const overlay = opts.overlay !== false;
  const gsap = window.gsap;

  const root = document.createElement('div');
  root.className = 'armock';
  root.innerHTML = `
    <div class="armock-label"><span class="armock-ico">${ICON_LEFT}</span><span class="armock-text"></span></div>
    <div class="armock-phone"><div class="armock-screen"><video muted loop playsinline preload="auto"></video></div><div class="armock-hole"></div></div>`;
  el.appendChild(root);
  if (!overlay) root.querySelector('.armock-label').style.display = 'none';

  const label = root.querySelector('.armock-label');
  const ico = root.querySelector('.armock-ico');
  const text = root.querySelector('.armock-text');
  const phone = root.querySelector('.armock-phone');
  const screen = root.querySelector('.armock-screen');
  const video = root.querySelector('video');
  video.muted = true;
  video.playsInline = true;
  video.setAttribute('muted', '');
  video.setAttribute('playsinline', '');

  let videoFailed = false;
  const failVideo = () => { videoFailed = true; screen.classList.add('armock-fail'); };
  video.addEventListener('error', failVideo);
  // The recording is a whole app session; by default loop only the AR
  // camera walk (146 s to the end). Pass start: 0, end: null for all of it.
  // If the server cannot seek (no HTTP Range support), play from 0 instead.
  let segStart = opts.start ?? (opts.video ? 0 : 146);
  const segEnd = opts.end ?? null;
  let seekWatch = 0;
  const canSeek = () => {
    const s = video.seekable;
    for (let i = 0; i < s.length; i++) if (s.start(i) <= segStart && s.end(i) > segStart + 1) return true;
    return false;
  };
  const toStart = () => {
    if (segStart <= 0 || !(video.duration > segStart)) return;
    if (!canSeek()) { segStart = 0; return; }
    try { video.currentTime = segStart; } catch (e) { segStart = 0; return; }
    clearTimeout(seekWatch);
    seekWatch = setTimeout(() => {
      if (video.readyState < 2 && video.seeking) { segStart = 0; try { video.currentTime = 0; } catch (e) { /* ignore */ } }
    }, 6000);
  };
  const onMeta = () => toStart();
  const onTime = () => {
    const end = segEnd ?? video.duration - 0.25;
    if (segStart > 0 && (video.currentTime >= end || video.currentTime < segStart - 1)) toStart();
  };
  const onEnded = () => { toStart(); if (playing) video.play().catch(() => {}); };
  video.addEventListener('loadedmetadata', onMeta);
  video.addEventListener('timeupdate', onTime);
  video.addEventListener('ended', onEnded);
  video.src = videoSrc;

  const setText = (n) => { text.innerHTML = `Turn left in <span class="armock-num">${n}</span> m`; };
  setText(6);

  // ---- Three.js ----
  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'high-performance' });
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  if (overlay) screen.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(60, 9 / 19.5, 0.05, 60);
  camera.rotation.order = 'YXZ';
  camera.rotation.x = THREE.MathUtils.degToRad(-18);
  camera.position.set(0, 1.5, 0);

  const curve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0, 0, 0.6),
    new THREE.Vector3(0, 0, -2),
    new THREE.Vector3(0, 0, -4.5),
    new THREE.Vector3(0, 0, -6),
    new THREE.Vector3(-0.45, 0, -6.95),
    new THREE.Vector3(-1.5, 0, -7.45),
    new THREE.Vector3(-3.2, 0, -7.6),
    new THREE.Vector3(-5.5, 0, -7.6),
  ], false, 'centripetal');

  const tex = makeChevronTexture();
  tex.anisotropy = renderer.capabilities.getMaxAnisotropy();
  const pathGeo = makeFlatTube(curve, 0.34, 240, 16);
  const pathMat = new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false, side: THREE.FrontSide });
  const path = new THREE.Mesh(pathGeo, pathMat);
  scene.add(path);

  const arrow = makeTurnArrow();
  arrow.group.position.set(-0.1, 0.5, -6.4);
  arrow.group.scale.setScalar(0.62);
  arrow.group.rotation.y = 0.35;
  scene.add(arrow.group);

  const reticleGeo = new THREE.RingGeometry(0.17, 0.205, 64);
  const reticleMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.75, depthWrite: false });
  const reticle = new THREE.Mesh(reticleGeo, reticleMat);
  reticle.rotation.x = -Math.PI / 2;
  const dotGeo = new THREE.CircleGeometry(0.035, 32);
  const dotMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.85, depthWrite: false });
  const dot = new THREE.Mesh(dotGeo, dotMat);
  dot.rotation.x = -Math.PI / 2;
  const reticleG = new THREE.Group();
  reticleG.add(reticle, dot);
  scene.add(reticleG);

  // ---- sizing ----
  function layout() {
    const w = root.clientWidth, h = root.clientHeight;
    if (!w || !h) return;
    const avail = Math.max(100, h - label.offsetHeight - 14);
    let ph = avail, pw = ph * 9 / 19.5;
    if (pw > w) { pw = w; ph = pw * 19.5 / 9; }
    phone.style.width = `${Math.round(pw)}px`;
    phone.style.height = `${Math.round(ph)}px`;
    const sw = Math.max(1, Math.round(pw - 18)), sh = Math.max(1, Math.round(ph - 18));
    renderer.setSize(sw, sh, false);
    camera.aspect = sw / sh;
    camera.updateProjectionMatrix();
    renderOnce();
  }
  const ro = new ResizeObserver(layout);
  ro.observe(root);

  // ---- animation state ----
  const state = { walk: 0, fade: 1 };
  let raf = 0, last = 0, t = 0, playing = false;

  function renderOnce() {
    camera.position.z = -state.walk;
    reticleG.position.set(0, 0.01, camera.position.z - 1.9);
    const s = 1 + 0.06 * Math.sin(t * 3);
    reticleG.scale.set(s, s, s);
    arrow.group.position.y = 0.5 + 0.06 * Math.sin(t * 2.2);
    arrow.group.rotation.y = 0.35 + 0.08 * Math.sin(t * 1.1);
    arrow.halo.material.opacity = 0.14 + 0.08 * (0.5 + 0.5 * Math.sin(t * 2.2));
    if (!overlay) return;
    renderer.domElement.style.opacity = String(state.fade);
    renderer.render(scene, camera);
  }

  function frame(now) {
    if (!playing) return;
    const dt = last ? Math.min(0.05, (now - last) / 1000) : 0;
    last = now;
    t += dt;
    tex.offset.y -= dt * 0.55; // chevrons flow forward
    renderOnce();
    raf = requestAnimationFrame(frame);
  }

  // ---- label / walk timeline ----
  let tl = null;
  function buildTimeline() {
    if (!gsap) return null;
    const cd = { p: 0 };
    let shown = 6;
    const tln = gsap.timeline({ paused: true, repeat: -1 });
    tln.call(() => {
      ico.innerHTML = ICON_LEFT; ico.style.background = '#ffb020';
      shown = 6; setText(6); state.walk = 0; state.fade = 1;
    }, null, 0);
    tln.fromTo(label, { opacity: 0, y: -8 }, { opacity: 1, y: 0, duration: 0.35, ease: 'power2.out' }, 0);
    tln.fromTo(cd, { p: 0 }, {
      p: 1, duration: 5, ease: 'none',
      onUpdate() {
        const n = Math.max(1, 6 - Math.floor(cd.p * 6));
        if (n !== shown) { shown = n; setText(n); }
      },
    }, 0);
    tln.fromTo(state, { walk: 0 }, { walk: 2.6, duration: 5, ease: 'none' }, 0);
    tln.to(label, { opacity: 0, y: -6, duration: 0.25, ease: 'power1.in' }, 5);
    tln.to(state, { fade: 0, duration: 0.3 }, 8.4);
    tln.call(() => {
      ico.innerHTML = ICON_ROOM; ico.style.background = '#ffd166';
      text.textContent = 'Room 1116 is on your right.';
    }, null, 5.25);
    tln.to(label, { opacity: 1, y: 0, duration: 0.35, ease: 'power2.out' }, 5.25);
    tln.to(label, { opacity: 0, duration: 0.3 }, 8.4);
    tln.set(state, { walk: 0 }, 8.7);
    tln.to(state, { fade: 1, duration: 0.3 }, 8.7);
    tln.to({}, { duration: 0.01 }, 9);
    return tln;
  }
  tl = overlay ? buildTimeline() : null;

  layout();

  return {
    play() {
      playing = true;
      if (!videoFailed) {
        if (segStart > 0) toStart(); else { try { video.currentTime = 0; } catch (e) { /* not loaded yet */ } }
        const p = video.play();
        if (p && p.catch) p.catch(() => { /* autoplay blocked; frame stays */ });
      }
      if (tl) tl.restart();
      cancelAnimationFrame(raf);
      last = 0;
      if (overlay) raf = requestAnimationFrame(frame);
    },
    pause() {
      playing = false;
      cancelAnimationFrame(raf);
      raf = 0;
      if (tl) tl.pause();
      try { video.pause(); } catch (e) { /* ignore */ }
    },
    destroy() {
      playing = false;
      cancelAnimationFrame(raf);
      if (tl) { tl.kill(); tl = null; }
      ro.disconnect();
      video.removeEventListener('error', failVideo);
      video.removeEventListener('loadedmetadata', onMeta);
      video.removeEventListener('timeupdate', onTime);
      video.removeEventListener('ended', onEnded);
      clearTimeout(seekWatch);
      try { video.pause(); } catch (e) { /* ignore */ }
      video.removeAttribute('src');
      video.load();
      pathGeo.dispose(); pathMat.dispose(); tex.dispose();
      reticleGeo.dispose(); reticleMat.dispose(); dotGeo.dispose(); dotMat.dispose();
      arrow.geometries.forEach((g) => g.dispose());
      arrow.materials.forEach((m) => m.dispose());
      renderer.dispose();
      renderer.forceContextLoss();
      root.remove();
    },
  };
}

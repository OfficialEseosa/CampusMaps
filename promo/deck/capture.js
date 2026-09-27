// Render a deck module to transparent PNG frames with a virtual clock.
// node capture.js <module> <seconds> <outDir> [--opts='{...}'] [--fps=30] [--w=1920 --h=1080] [--warm=0.5] [--pre='js']
// --pre runs JS in the page after mount (e.g. to call __handle.play()). Frames: f_00000.png ...
const p = require('puppeteer-core');
const fs = require('fs');
const path = require('path');

const [name, secs, outDir] = process.argv.slice(2);
const flag = (k, d) => { const a = process.argv.find(x => x.startsWith(`--${k}=`)); return a ? a.slice(k.length + 3) : d; };
const fps = +flag('fps', 30), W = +flag('w', 1920), H = +flag('h', 1080);
const opts = flag('b64') ? Buffer.from(flag('b64'), 'base64').toString() : flag('opts', '{}');
const pre = flag('pre', ''), warm = +flag('warm', 0.5);
const port = flag('port', '8770');

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const b = await p.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: 'new',
    userDataDir: 'C:/Users/rapha/AppData/Local/Temp/claude/edge-cap-' + process.pid,
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required', `--window-size=${W},${H}`],
  });
  const pg = await b.newPage();
  await pg.setViewport({ width: W, height: H, deviceScaleFactor: 1 });
  pg.on('console', m => { if (m.type() === 'error') console.log('[console]', m.text()); });
  pg.on('pageerror', e => console.log('[pageerror]', e.message));
  await pg.goto(`http://localhost:${port}/capture.html?m=${name}&opts=${encodeURIComponent(opts)}&v=${Date.now()}`, { waitUntil: 'load', timeout: 60000 });
  await pg.waitForFunction('window.__ready === true', { timeout: 60000, polling: 100 });
  if (pre) await pg.evaluate(pre);
  // Let async data loads (fetch of building JSON) settle, ticking the virtual clock meanwhile.
  for (let i = 0; i < warm * fps; i++) { await pg.evaluate(ms => window.__vt.step(ms), 1000 / fps); await new Promise(r => setTimeout(r, 20)); }
  const n = Math.round(secs * fps);
  for (let i = 0; i < n; i++) {
    await pg.evaluate(ms => window.__vt.step(ms), 1000 / fps);
    await pg.screenshot({ path: path.join(outDir, `f_${String(i).padStart(5, '0')}.png`), omitBackground: true });
    if (i % fps === 0) console.log(`t=${i / fps}s`);
  }
  await b.close();
  try { fs.rmSync('C:/Users/rapha/AppData/Local/Temp/claude/edge-cap-' + process.pid, { recursive: true, force: true }); } catch {}
  console.log('done', n, 'frames in', outDir);
})();
